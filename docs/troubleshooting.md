# Problèmes rencontrés — isolation & CI/CD (retour d'expérience)

[← Sommaire](00-getting-started.md)

Ce fichier liste, dans l'ordre où on les a rencontrés, les problèmes réels tombés pendant la mise en place de l'isolation `lets-travel` / `travel-plan` et du pipeline CI/CD — y compris les petits. Utile pour l'audit : montrer qu'on sait *pourquoi* ça a cassé et *comment* on l'a diagnostiqué compte souvent plus que de prétendre que tout a marché du premier coup.

## 1. Collision de ports/noms entre les deux stacks Docker

**Problème** : `lets-travel` est dérivé de `travel-plan`, donc au départ les deux repos utilisaient exactement les mêmes ports hôte (Postgres `5434`, Neo4j `7474`/`7687`, Nginx `80`/`443`, Jenkins `8090`, SonarQube `9000`) et le même nom de projet Compose (`travel-plan-app`, `travel-plan-ci`). Lancer `lets-travel` en gardant `travel-plan` up en parallèle aurait fait planter le `docker compose up` du second (ports déjà pris) et/ou renommé/écrasé les conteneurs du premier (même nom de projet = Compose les considère comme "les siens").

**Solution** : renommage complet des deux `name:` Compose (`lets-travel-app`, `lets-travel-ci`) et décalage de tous les ports publiés côté `lets-travel` (`5435`, `7475`/`7688`, `8080`/`8443`, `8091`, `9001`) — voir [`01-ci-cd.md`](01-ci-cd.md) et [`02-app-infra.md`](02-app-infra.md) pour le détail. Les instances `travel-plan` restent intactes, sur leurs ports/noms d'origine, avec leur historique.

## 2. Le certificat TLS interne ne "sait" plus qui il est censé couvrir

**Problème** : le certificat auto-signé Nginx/inter-services embarque un `SAN` (Subject Alternative Name) listant les noms DNS des conteneurs auxquels il doit correspondre — et ces noms dépendent du nom de projet Compose (`travel-plan-app-auth-service-1`, etc.). En renommant le projet en `lets-travel-app`, l'ancien certificat (généré une première fois pour `travel-plan`) devient invalide pour les nouveaux noms de conteneurs.

**Solution** : `ansible/playbooks/deploy.yml` génère le certificat avec `creates: <chemin>` — un garde-fou qui dit à Ansible "ne régénère pas si le fichier existe déjà". Comme l'ancien certificat existait toujours (généré du temps où c'était `travel-plan-app`), il fallait le supprimer à la main pour forcer Ansible à en générer un nouveau avec les bons noms au prochain déploiement. À refaire à chaque fois que le nom de projet Compose change.

## 3. `HOST_REPO_PATH` dans `infra/ci/.env` pointait encore vers l'ancien repo

**Problème** : ce chemin sert au conteneur Jenkins (Docker-outside-of-Docker) à retrouver le vrai chemin hôte du repo pour ses bind-mounts pendant le déploiement — sans ça, le stage `Deploy` du pipeline monte le mauvais dossier. Le fichier `.env` avait été copié depuis `travel-plan` sans que cette valeur soit mise à jour : elle pointait toujours vers `.../travel-plan` au lieu de `.../lets-travel`.

**Solution** : correction directe de la valeur dans `infra/ci/.env` (fichier non commité, contient des secrets — jamais dans Git). Sans rapport direct avec l'isolation des ports, mais repéré en même temps en vérifiant ce fichier.

## 4. Croire avoir mergé une branche alors qu'on a juste changé de branche

**Problème** : après avoir travaillé sur `chore/isolate-dev-ports`, `git checkout main` suivi d'un `git status` propre ("up to date with origin/main") a donné l'impression que la branche avait été mergée. En réalité, changer de branche ne merge rien — `main` était resté sur son ancien commit, et la branche de travail restait à part, seulement poussée sur GitHub sans PR mergée.

**Comment on l'a détecté** : `git log --all --oneline`, `git branch -a` et `git log origin/main` vs `git log origin/chore/isolate-dev-ports` montrent clairement deux historiques qui divergent encore. Rien n'était perdu, juste pas fusionné.

**À retenir** : `git status` "clean" sur `main` veut seulement dire "aucune modif locale non commitée sur la branche actuelle" — ça ne dit rien sur l'état des autres branches. Merger se fait explicitement (PR GitHub ou `git merge`), jamais implicitement en changeant de branche.

## 5. La stack `lets-travel` n'avait en réalité jamais démarré

**Problème** : en vérifiant l'isolation des ports après le merge, on a découvert que `infra/nginx/certs/` et `infra/internal-tls/certs/` ne contenaient que des `.gitkeep` — aucun certificat généré. `./scripts/start-app.sh` fait un simple `docker compose up`, qui aurait monté un dossier vide à la place d'un fichier de certificat attendu (Nginx et les services auraient planté au démarrage).

**Solution retenue** : plutôt que lancer Ansible à la main pour ce premier démarrage, on a choisi de finaliser le job Jenkins d'abord — son stage `Deploy` appelle déjà `ansible-playbook` (voir [`08-ansible-deploy-tls.md`](08-ansible-deploy-tls.md)), donc le premier vrai démarrage sert aussi à valider que le pipeline CI/CD fonctionne de bout en bout, plutôt que de faire le travail deux fois.

## 6. `mvnw` a perdu son bit exécutable — Jenkins échoue avec `Permission denied` (exit 126)

**Problème** : le build Jenkins échouait sur les 5 microservices avec `./mvnw: Permission denied`. Diagnostic : `git ls-files --stage backend/*/mvnw` montrait le mode `100644` (non-exécutable) côté `lets-travel`, contre `100755` (exécutable) côté `travel-plan` — pour le *même* blob (hash identique, donc contenu strictement identique). Cause probable : Git ne suit pas fiablement le bit d'exécution Unix sur un checkout via WSL2 sur un disque Windows monté (`/mnt/d/...`) — le bit s'est perdu au moment du premier commit du fichier dans `lets-travel`.

**Solution** : `git update-index --chmod=+x backend/*/mvnw` sur les 5 fichiers — ne change que le mode suivi par Git (métadonnée), aucune ligne de contenu modifiée (vérifiable : le hash du blob reste le même). Poussé sur `fix/mvnw-permissions`, corrige le build sans toucher au code.

**Comment vérifier soi-même** : comparer `git ls-files --stage backend/*/mvnw` entre les deux repos (mêmes hash de blob, modes différents) est la preuve la plus directe — pas besoin de deviner, la commande le montre noir sur blanc.

## 7. Scanner SonarQube en échec avec `401 Unauthorized` malgré un `SONAR_TOKEN` déjà rempli

**Problème** : une fois le build/tests passés (voir point 6), le stage `Sonar` échouait sur les 5 services avec `Failed to query server version: ... HTTP 401 Unauthorized`, alors que `SONAR_TOKEN` n'était pas vide dans `infra/ci/.env`. Deux causes distinctes, empilées : (1) `infra/ci/jenkins/casc.yaml` ne lit `${SONAR_TOKEN}` qu'**au démarrage** du conteneur Jenkins pour créer le credential `sonar-token` — modifier `.env` après coup ne suffit pas, il faut recréer le conteneur ; (2) la valeur déjà présente dans `.env` n'avait en réalité jamais été générée sur l'instance `lets-travel-sonarqube` elle-même (page "Generate Tokens" → "No tokens") — probablement copiée depuis `travel-plan` au moment du bootstrap du repo, donc invalide ici même après recréation du conteneur.

**Solution** : générer un vrai token sur l'instance concernée (**http://localhost:9001**, pas `:9000` qui est celui de `travel-plan`) via *My Account → Security → Generate Tokens* (type "Global Analysis Token" ou "User Token"), le coller dans `SONAR_TOKEN` de `infra/ci/.env`, puis forcer la recréation du conteneur Jenkins pour qu'il relise la valeur :
```bash
cd infra/ci
docker compose up -d --force-recreate jenkins
```

**À retenir** : sur ce projet, toute variable d'`infra/ci/.env` consommée par `casc.yaml` (`GITHUB_TOKEN`, `SONAR_TOKEN`, mot de passe admin Jenkins) n'est prise en compte qu'au démarrage du conteneur — un `.env` modifié à chaud ne suffit jamais, il faut recréer (`--force-recreate`) le service concerné.

## 8. `auth-service` ne compile plus après ajout de validations Bean Validation (`jakarta.validation` introuvable)

**Problème** : en ajoutant `@Valid`/`@NotBlank`/`@NotNull`/`@AssertTrue` sur un nouveau DTO d'`auth-service` (branche `feat/travel-manager-role`), le build échoue avec `package jakarta.validation does not exist`. Cause : `auth-service` n'avait jusque-là jamais eu besoin de Bean Validation (`LoginRequest` n'a aucune annotation de validation), donc son `pom.xml` n'a jamais inclus `spring-boot-starter-validation` — contrairement à `travel-service`, qui l'a depuis le début (`TravelRequest` en a besoin).

**Solution** : ajouter la dépendance manquante dans `backend/auth-service/pom.xml` :
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

**À retenir** : avant d'ajouter des annotations `@Valid`/`jakarta.validation.*` à un service, vérifier que `spring-boot-starter-validation` est bien dans son `pom.xml` — chaque microservice a ses propres dépendances, ce n'est pas parce qu'un service voisin (ici `travel-service`) l'a déjà que tous l'ont.

## 9. `TravelServiceApplicationTests.contextLoads` échoue après ajout d'un nouveau repository (`SubscriptionRepository`)

**Problème** : en ajoutant `SubscriptionRepository`/`SubscriptionService` (branche `feat/traveler-subscriptions`), `mvn test` échoue avec `UnsatisfiedDependencyException: ... No qualifying bean of type 'SubscriptionRepository' available`, sur le test `contextLoads` uniquement (83 tests run, 1 error) — tous les tests unitaires/MockMvc passent, seul le chargement du contexte Spring complet plante.

**Cause** : `TravelServiceApplicationTests` est un test de "smoke" qui vérifie que le contexte Spring démarre sans vraie infrastructure (pas de vrai Postgres/Neo4j) : il exclut `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration`/`Neo4jAutoConfiguration` via `@EnableAutoConfiguration(exclude = ...)`, ce qui désactive la création automatique des proxies Spring Data JPA. À la place, chaque repository utilisé quelque part dans l'appli est déclaré à la main avec `@MockitoBean` (`travelRepository`, `placeRepository`, etc.). En ajoutant `SubscriptionRepository` (injecté dans le nouveau `SubscriptionService`) sans ajouter son `@MockitoBean` correspondant dans cette classe de test, Spring ne trouve plus aucun bean — ni réel (JPA désactivé), ni mocké (oublié) — pour le construire.

**Solution** : ajouter le mock manquant dans `TravelServiceApplicationTests.java` :
```java
@MockitoBean
private SubscriptionRepository subscriptionRepository;
```

**À retenir** : `TravelServiceApplicationTests` doit être mis à jour à chaque nouveau repository Spring Data JPA ajouté à `travel-service`, pas seulement les tests unitaires du nouveau code — sinon le smoke test de démarrage du contexte casse silencieusement, même si tout le reste (services, controllers, DB réelle en local) fonctionne.

## 10. SonarQube (Quality Gate "New Code") : `Instant.now()`/`LocalDate.now()` sans fuseau explicite (règle `S6355`)

**Problème** : le scan SonarQube du PR `feat/traveler-subscriptions` échoue avec *"Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method."* sur `SubscriptionService.java` (2x `Instant.now()`, 1x `LocalDate.now()` pour le cutoff d'annulation à 3 jours). La règle ne s'applique qu'au "New Code" (lignes ajoutées dans cette branche) — le code pré-existant qui fait la même chose (`Travel.java`, `ApiExceptionHandler.java`) n'est pas remonté, car non modifié par cette branche.

**Solution** : introduction d'un bean `Clock` unique (`config/ClockConfig.java`, `Clock.systemUTC()`), injecté dans `SubscriptionService` via son constructeur (`@RequiredArgsConstructor` le prend automatiquement en ajoutant le field `private final Clock clock;`), puis `Instant.now(clock)`/`LocalDate.now(clock)` à la place des appels nus. Bénéfice au passage : `SubscriptionServiceTest` utilise maintenant une `Clock.fixed(...)` au lieu de dépendre de l'horloge système au moment où les tests tournent (plus déterministe, notamment pour les tests du cutoff à J-3 pile). Les fichiers de test qui n'avaient besoin que d'un timestamp arbitraire (pas de logique métier testée) sont passés à `Instant.now(Clock.systemUTC())` — suffisant pour satisfaire la règle sans introduire de dépendance inutile.

**À retenir** : ne plus jamais utiliser `Instant.now()`/`LocalDate.now()`/`LocalDateTime.now()` sans argument dans du nouveau code sur ce projet — toujours passer le bean `Clock` (`clock()` dans `ClockConfig`) par injection de dépendances. Ça satisfait Sonar et ça rend le code testable avec une horloge fixe plutôt que de dépendre de l'instant réel d'exécution.

## 11. `payment-service` ne démarre plus : pas de bean `RestClient.Builder` auto-configuré (contrairement à l'hypothèse initiale)

**Problème** : en ajoutant le premier appel HTTP inter-service du projet (`feat/travel-pricing-and-traveler-payment`, `TravelServiceClient` → `travel-service`), `PaymentServiceApplicationTests.contextLoads` échoue avec `NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.web.client.RestClient$Builder' available`. La conception initiale de `TravelServiceClientConfig` supposait qu'un bean `RestClient.Builder` auto-configuré par Spring Boot existait déjà dans `payment-service` (comme c'est le cas dans beaucoup de projets Spring Boot classiques) et se contentait de le cloner (`.clone()`) pour y ajouter `@LoadBalanced`. Faux dans ce projet : `PaymentProviderConfig.paymentRestClient()` (utilisé par Stripe/PayPal) construit déjà son `RestClient` à la main via `RestClient.create()`, sans jamais passer par un `RestClient.Builder` injecté — signe qu'aucune configuration automatique de ce type ne s'active dans `payment-service` (`spring-boot-starter-webmvc` seul ne suffit pas à la déclencher dans cette version de Spring Boot).

**Solution** : `TravelServiceClientConfig.loadBalancedRestClientBuilder()` construit désormais son `RestClient.Builder` explicitement via `RestClient.builder()`, sans dépendre d'un bean auto-configuré. Pour la confiance TLS interne (bundle `internal-services`, uniquement en profil docker), le `SSLContext` est construit à la main via `SslBundles.getBundle(...).createSslContext()` (injecté conditionnellement — `spring.http.client.ssl.bundle` est vide en profil par défaut, donc ce chemin ne s'exécute jamais pendant `mvn test`) et attaché à un `JdkClientHttpRequestFactory` (`java.net.http.HttpClient` avec `.sslContext(...)`).

**À retenir** : ne jamais supposer qu'un bean Spring Boot "standard" (`RestClient.Builder`, `RestTemplateBuilder`, etc.) est auto-configuré dans un service donné sans vérifier d'abord si ce service en a déjà un usage existant (ici, `PaymentProviderConfig` le prouvait déjà indirectement). En cas de doute et sans pouvoir compiler localement (Maven Central bloqué côté agent), préférer construire le bean explicitement (`RestClient.builder()`) plutôt que d'injecter un bean supposé auto-configuré — le pire qui puisse arriver avec la construction explicite est un TLS mal configuré (visible immédiatement au premier appel réel), alors qu'une injection ratée casse le démarrage complet du contexte Spring.

## 12. `ApiExceptionHandler` renvoie 500 au lieu de 400 pour un `@RequestHeader` obligatoire manquant

**Problème** : `PaymentControllerTest.createReturns400WhenAuthorizationHeaderMissing` échoue (`Status expected:<400> but was:<500>`). `POST /api/payments` déclare `@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader` (obligatoire, pas de valeur par défaut) pour propager le JWT vers `travel-service`. Sans ce header, Spring MVC lève `MissingRequestHeaderException`, que `ApiExceptionHandler` ne traitait pas explicitement — elle matchait donc le handler générique `@ExceptionHandler(Exception.class)` (500), au lieu du traitement 400 que Spring applique par défaut *en l'absence* de tout `@ExceptionHandler` local correspondant. Un `@RestControllerAdvice` avec un handler générique `Exception.class` intercepte tout, y compris les exceptions que Spring aurait autrement mappées correctement lui-même.

**Solution** : ajout d'un `@ExceptionHandler(MissingRequestHeaderException.class)` explicite dans `ApiExceptionHandler`, renvoyant 400 avec le nom du header manquant dans le message.

**À retenir** : dès qu'un controller de ce projet ajoute un `@RequestHeader` (ou tout autre paramètre de requête) obligatoire, vérifier que l'exception Spring correspondante (`MissingRequestHeaderException`, `MissingServletRequestParameterException`, etc.) a bien un handler dédié dans `ApiExceptionHandler` — le handler générique `Exception.class` présent dans ce projet masque silencieusement le comportement 400 par défaut de Spring pour toute exception non explicitement gérée.

## 13. SonarQube (Quality Gate "New Code") : littéral `"/api/travels/**"` dupliqué 3x dans `SecurityConfig` (règle `S1192`)

**Problème** : le scan SonarQube de la PR `feat/travel-pricing-and-traveler-payment` échoue sur `travel-service` avec *"Define a constant instead of duplicating this literal '/api/travels/**' 3 times."*. Cause : `SecurityConfig` utilisait déjà ce littéral pour les règles `PUT`/`DELETE` (héritées de `feat/travel-manager-role`) ; cette branche a ajouté une 3e occurrence pour la nouvelle règle `GET` (ouverture aux Travelers pour la consultation des prix/abonnements) — ce qui a fait passer le compteur au-dessus du seuil de duplication de Sonar, uniquement détecté sur le "New Code" de cette PR.

**Solution** : extraction d'une constante `TRAVELS_WILDCARD = "/api/travels/**"`, réutilisée pour les 3 `requestMatchers` (`PUT`, `DELETE`, `GET`).

**À retenir** : dès qu'un `requestMatchers(...)` réutilise un pattern d'URL déjà présent ailleurs dans le même `SecurityConfig`, vérifier s'il vaut mieux l'extraire en constante *avant* de pousser — Sonar ne le remontera que si le nouveau code fait franchir le seuil de duplication (donc invisible en relisant seulement le diff de la branche, il faut avoir en tête l'état du fichier dans son ensemble).

## 14. Le formulaire de voyage frontend n'aurait plus jamais pu créer/modifier un voyage (`ownerId`/`managerId`, `price`/`currency`)

**Problème** : en implémentant `feat/manager-frontend`, la lecture de `TravelRequest.java` a révélé que le frontend Angular (`core/models/travel.ts`, `travel-form.ts`/`.html`, `travel-list.html`) n'avait jamais été mis à jour après deux changements de contrat antérieurs sur `travel-service` : (1) `feat/travel-manager-role` a renommé `Travel.ownerId` en `managerId` (voir section dédiée de `nouveautes-vs-travel-plan.md`) — le frontend envoyait toujours `ownerId` ; (2) `feat/travel-pricing-and-traveler-payment` a rendu `price`/`currency` obligatoires sur `TravelRequest` (`@NotNull @Positive` / `@NotBlank`) — le formulaire ne les proposait pas du tout. Conséquence : toute création ou modification de voyage depuis l'UI admin échouait silencieusement en 400 Bean Validation depuis l'introduction de ces deux branches, sans que personne ne l'ait remarqué (le formulaire n'avait apparemment pas été retesté depuis).

**Comment on l'a détecté** : pas via un plantage observé en cours de session, mais en relisant `TravelRequest.java`/`Travel.java` par prudence avant de brancher `feat/manager-frontend` dessus (le dashboard manager dépend du prix des voyages pour son estimation de revenu) — la faille aurait été invisible tant que personne n'essayait de créer un voyage depuis l'UI.

**Solution** : renommage `ownerId` → `managerId` dans le modèle et partout où il est utilisé, ajout des champs `price`/`currency` au formulaire (même pattern que `payment-form` : champ texte 3 lettres pour la devise, défaut `EUR`), et rôle-conditionnalité du champ manager — un ADMIN choisit explicitement un `TRAVEL_MANAGER` dans une liste déroulante, un `TRAVEL_MANAGER` a le contrôle désactivé et forcé à son propre `userId` (le backend l'ignore de toute façon pour cet appelant, voir `TravelService.resolveManagerId`).

**À retenir** : un renommage ou un changement de contrat backend (`TravelRequest`, `TravelResponse`) n'est pas terminé tant que le frontend qui l'appelle n'a pas été vérifié — même si la branche qui l'a introduit ne touchait "que" le backend. Rien dans la CI actuelle (pas de test e2e frontend↔backend) n'aurait rattrapé cette dérive.

## 15. Impossible de créer un compte Travel Manager depuis l'UI admin

**Problème** : `user-service`'s `Role` enum a trois valeurs depuis `feat/travel-manager-role` (`TRAVELER`, `TRAVEL_MANAGER`, `ADMIN`), mais le frontend (`core/models/user.ts` → `UserRole`, et le tableau `UserForm.roles`) était resté bloqué sur `['TRAVELER', 'ADMIN']`. Aucun formulaire ne permettait donc de créer ou promouvoir un compte manager : `feat/manager-frontend` (dashboard + profil public manager) n'avait littéralement aucun compte à connecter pour être testé de bout en bout.

**Solution** : ajout de `'TRAVEL_MANAGER'` à `UserRole` et au tableau `roles` de `UserForm`, plus une variante de badge dédiée (`warning`) pour le distinguer visuellement d'`ADMIN`/`TRAVELER` dans les tableaux (`shared/ui/badge.ts`).

**À retenir** : même symptôme que le point 14 — un rôle ajouté côté backend (`feat/travel-manager-role`) doit aussi être propagé à tous les formulaires frontend qui énumèrent des rôles en dur, pas seulement aux règles d'autorisation `SecurityConfig`.

## 16. `GET /api/users/{id}` interdit à un Travel Manager — impossible d'afficher le profil d'un abonné

**Problème** : `user-service`'s `SecurityConfig` réservait toute route (sauf l'inscription publique) à `ADMIN` (`anyRequest().hasRole("ADMIN")`), sans exception. L'énoncé (`docs/lets-travel_project.md`, section Travel Manager) demande explicitement de pouvoir "view profiles" des abonnés à ses voyages — impossible avec cette règle telle quelle : un appel `GET /api/users/{id}` avec un JWT `TRAVEL_MANAGER` recevait un 403.

**Solution** : ajout d'une règle dédiée `GET /api/users/*` (lookup par id — pas `GET /api/users` sans suffixe, qui reste la liste complète) ouverte à `ADMIN` et `TRAVEL_MANAGER`, placée avant le `anyRequest().hasRole("ADMIN")` catch-all. Le contrôle fin ("seulement les abonnés de SES voyages à lui") reste fait côté `travel-service` (`SubscriptionService.requireManagerOwnershipOrAdmin`) : `user-service` n'a aucun moyen de savoir de quel voyage provient l'appel, il ne peut trancher que grossièrement par rôle.

**À retenir** : `user-service` n'a pas de `RoleHierarchy` (contrairement à `travel-service`) — un `hasAnyRole(...)` doit donc lister explicitement chaque rôle autorisé (`ADMIN`, `TRAVEL_MANAGER`), un `hasRole(TRAVEL_MANAGER_ROLE)` seul n'aurait pas laissé passer un `ADMIN`.

## 17. SonarQube (frontend, `lets-travel-frontend`) : tests sans assertion "reconnue" et assertions génériques (`.length`/`.toBe` au lieu de `.toHaveLength`)

**Problème** : le scan Sonar de `feat/manager-frontend` (frais, "Last analysis" à quelques minutes) fait échouer le Quality Gate avec deux règles orientées qualité de tests TypeScript/Vitest, jamais vues avant sur ce projet :
- **Blocker** *"Add at least one assertion to this test case"* sur deux tests qui ne contenaient que des `httpMock.expectOne(...)`/`httpMock.expectNone(...)` (`dashboard.spec.ts` L81, `travel-form.spec.ts` L153) — fonctionnellement ces appels *sont* des assertions (ils font échouer le test si l'appel HTTP attendu n'a pas/a eu lieu), mais Sonar ne reconnaît que les `expect(...)` littéraux de Vitest/Chai, pas les appels d'assertion propres à `HttpTestingController`.
- **Low** *"Prefer a more specific assertion instead of this generic one"* sur 9 occurrences du pattern `expect(x.length).toBe(N)` dans `travel-form.spec.ts` (L41/46/49/53/56/59/74/77/80) — Sonar préfère `expect(x).toHaveLength(N)`, qui produit un message d'échec plus lisible (affiche le contenu du tableau, pas juste les deux longueurs).

**Solution** : pour les deux Blocker, fusion du test sans assertion avec le test voisin qui vérifie déjà un état par `expect(...)` (même `beforeEach`, même scénario) — le `httpMock.expectNone(...)` devient une vérification supplémentaire dans un test qui contient déjà un `expect()` réel, au lieu de vivre seul dans son propre `it(...)`. Pour les 9 Low, remplacement mécanique de `expect(x.length).toBe(N)` par `expect(x).toHaveLength(N)`, sans changement de logique.

**À retenir** : sur ce projet, un test qui ne fait *que* du `httpMock.expectOne/expectNone(...)` sans aucun `expect(...)` littéral déclenche un Blocker Sonar, même si le test est fonctionnellement valide — toujours regrouper ce genre de vérification HTTP avec un test voisin qui a déjà une assertion d'état, plutôt que de lui laisser son propre `it(...)` isolé. Et préférer systématiquement les matchers Vitest spécifiques (`toHaveLength`, `toContain`, `toBeNull`, etc.) à leur équivalent générique (`.length).toBe(...)`, `.toBe(null)`...) dès l'écriture du test, pas seulement en correction après coup.

## 18. Stage Deploy Jenkins : `vault operator unseal` échoue avec une clé périmée, ET le dossier de sauvegarde des secrets CI (`infra/ci/persistent-state`) n'existe nulle part sur le disque

**Problème** : le stage Deploy plante dès la 1ère tâche utile (`vault-unseal.yml`, "Unseal Vault using the stored key") avec `FAILED! => {"censored": true, ...}` — le message réel est caché par `no_log: true` (pour ne jamais logguer la clé). En creusant : `vault status` renvoie `initialized: true` sur le volume Docker `vault_data` du stack CI (`infra/ci/deploy-workspace`), une clé de descellement a bien été retrouvée, mais elle est rejetée par Vault — clé sur disque qui ne correspond plus aux données réelles du volume (même famille que l'incident Vault local dev, mais un Vault CI totalement distinct, avec sa propre clé stockée ailleurs).

En voulant vérifier/nettoyer cette clé, découverte d'un 2e problème, plus profond : le `Jenkinsfile` (stage Deploy) sauvegarde les secrets gitignorés (`.env`, clés Vault, certs TLS) dans `$HOST_REPO_PATH/infra/ci/persistent-state` avant de vider/recréer `deploy-workspace` à chaque build (pour survivre au `rm -rf` + réextraction d'un tar frais depuis le checkout Git). Mais `infra/ci/docker-compose.yml` (service `jenkins`, bloc `volumes`) ne monte QUE `${HOST_REPO_PATH}/infra/ci/deploy-workspace` dans le conteneur Jenkins — pas son dossier frère `persistent-state`. Résultat : `persistent-state` n'a jamais existé sur le vrai disque D: (confirmé indépendamment via `device_list_dir` et `device_bash`, et par l'utilisateur lui-même en natif) — il ne vit que dans la couche interne éphémère du conteneur `lets-travel-jenkins`, malgré un chemin affiché dans les logs qui ressemble à un vrai chemin `/mnt/d/...`.

**Solution appliquée (déblocage immédiat, pas de changement de code)** : suppression de la clé périmée aux DEUX endroits où elle existe — la copie réelle sur disque (`infra/ci/deploy-workspace/infra/vault/.unseal-key.txt`) ET la copie cachée dans le conteneur Jenkins (`infra/ci/persistent-state/infra/vault/.unseal-key.txt`, atteignable uniquement via `docker exec`) — en une seule commande lancée par l'utilisateur (pas moi, pas d'accès Docker depuis l'agent) :
```
docker exec lets-travel-jenkins sh -c 'rm -f "$HOST_REPO_PATH/infra/ci/deploy-workspace/infra/vault/.unseal-key.txt" "$HOST_REPO_PATH/infra/ci/persistent-state/infra/vault/.unseal-key.txt"'
```
Supprimer seulement l'une des deux copies ne suffit pas : le manège `mv` (avant le wipe) puis `cp` (après) à chaque build ne fait que déplacer temporairement le fichier qui est déjà là — si l'une des deux copies survit, elle revient à l'identique au build suivant. Une fois les deux effacées, le self-heal déjà écrit dans `vault-unseal.yml` (bloc "Vault initialized but unseal key missing") détecte "initialisé + clé absente" et réinitialise proprement le volume tout seul.

**À retenir** :
1. Le Vault CI (`infra/ci/deploy-workspace`) et le Vault dev local (racine du repo) sont deux instances distinctes, avec des clés stockées à des endroits différents — ne jamais confondre les deux en diagnostiquant un souci de scellement.
2. Quand un fichier "persisté" par un script Jenkins semble introuvable sur le disque, vérifier D'ABORD le mapping `volumes:` du service Jenkins dans `infra/ci/docker-compose.yml` avant de soupçonner un souci de cache WSL2/DrvFs — un chemin qui ressemble à `/mnt/d/...` dans un log Jenkins n'est pas forcément monté sur le vrai disque si le `docker-compose.yml` du conteneur Jenkins ne le liste pas explicitement dans ses `volumes`.
3. **Corrige** : `infra/ci/persistent-state` est desormais monte dans le conteneur Jenkins (`infra/ci/docker-compose.yml`, service `jenkins`), au meme titre que `deploy-workspace` - la sauvegarde des secrets survit maintenant a une recreation du conteneur `lets-travel-jenkins`, pas seulement a sa duree de vie. Limite qui reste malgre ce fix : si Vault est reinitialise EN DEHORS du flux Jenkins (comme l'incident du meme jour ou `ansible-playbook site.yml` a ete relance en local), la copie de secours redevient perimee par rapport a la realite - ce n'est plus un bug de montage, juste le prix normal d'une sauvegarde qui ne peut refleter que le dernier etat connu.

## 19. `mvn test` sur `travel-service` échoue à résoudre `org.testcontainers:elasticsearch` — l'artifact a changé de nom en Testcontainers 2.x

**Problème** : en ajoutant le module Testcontainers Elasticsearch (pour `RecommendationRepositoryTest`/`TravelSearchServiceTest` de `feat/search-and-recommendations`), deux échecs successifs de `mvn test`, chacun révélant une couche du même problème :
1. **1er run** : `'dependencies.dependency.version' for org.testcontainers:elasticsearch:jar is missing` — corrigé en ajoutant `<version>${testcontainers.version}</version>` (propriété exposée par `spring-boot-starter-parent` pour importer le `testcontainers-bom`), sur le modèle habituel.
2. **2e run** (après le fix ci-dessus) : `Could not find artifact org.testcontainers:elasticsearch:jar:2.0.5 in central` — la version `2.0.5` existe bien (résolue via `${testcontainers.version}`, gérée par `spring-boot-starter-parent` 4.1.0), mais PAS sous cet artifactId : recherche sur Maven Central, l'artifactId `elasticsearch` nu s'arrête à la série `1.x` (dernière version `1.21.4`) — Testcontainers a renommé ce module `testcontainers-elasticsearch` (avec le préfixe `testcontainers-`) à partir de la série `2.x`, la même convention que `testcontainers-neo4j`/`testcontainers-junit-jupiter` déjà présents dans ce `pom.xml` juste au-dessus. L'ancien nom `elasticsearch` n'a simplement jamais été republié en `2.0.x`.

**Solution** : artifactId corrigé en `testcontainers-elasticsearch` (version `${testcontainers.version}` conservée) :
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-elasticsearch</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
```

**À retenir** : sur ce projet (Testcontainers 2.x via `spring-boot-starter-parent` 4.1.0), tous les artifactId de modules Testcontainers suivent la convention `testcontainers-<module>` (`testcontainers-neo4j`, `testcontainers-junit-jupiter`, `testcontainers-elasticsearch`...) — un ancien nom nu (`elasticsearch`, `neo4j`, `postgresql`...) sans le préfixe appartient à la série `1.x` et ne résout plus rien en `2.x`. En cas de "version manquante" sur un module Testcontainers, vérifier `${testcontainers.version}` d'abord ; en cas d'échec de résolution APRÈS avoir ajouté la version (artifact introuvable dans central malgré une version qui existe bel et bien pour ce groupId), vérifier que l'artifactId suit bien la convention `testcontainers-<module>` de la série 2.x plutôt que l'ancien nom nu de la série 1.x.

## 20. `TravelSearchServiceTest` : `UnfinishedStubbingException` en essayant de mocker `Hit`/`HitsMetadata`/`SearchResponse` du client `elasticsearch-java`

**Problème** : les tests `searchReturnsIdsFromHitsInOrder` et `autocompleteReturnsIdsFromHits` de `feat/search-and-recommendations` échouaient avec `org.mockito.exceptions.misusing.UnfinishedStubbingException` sur `when(hit.id()).thenReturn(...)`, quelle que soit la façon d'écrire la construction des mocks (`.stream().map(...)` puis boucle `for` imperative testées, même résultat les deux fois). Le message Mockito ("Unfinished stubbing detected... E.g. thenReturn() may be missing") est trompeur : il pointe normalement vers un stub mal formé, pas vers la vraie cause ici.

**Comment la vraie cause a été trouvée** : en lisant directement le rapport Surefire complet sur disque (`target/surefire-reports/....TravelSearchServiceTest.txt`, plus complet que la sortie console tronquée collée dans le terminal), la stack trace montrait `at co.elastic.clients.elasticsearch.core.search.Hit.id(Hit.java:154)` — c'est-à-dire que le VRAI corps de la méthode `Hit.id()` s'exécutait au lieu d'être intercepté par Mockito, et levait une exception que Mockito rapportait ensuite comme un stubbing non terminé. `Hit`, `HitsMetadata` et `SearchResponse` sont des classes concrètes "record-like" (générées, avec constructeur privé et validation `ApiTypeHelper.requireNonNull` sur leurs champs obligatoires) du client bas niveau `co.elastic.clients:elasticsearch-java` — contrairement à `ElasticsearchClient`, qui est une interface et se mock sans aucun souci dans les autres tests de ce même fichier.

**Solution** : ne plus mocker `Hit`/`HitsMetadata`/`SearchResponse`, construire de vraies instances via leurs Builders publics. Champs obligatoires vérifiés directement sur le code source du client (dépôt GitHub `elastic/elasticsearch-java`, tag `v8.11.1` — celui utilisé par ce `pom.xml` — pas devinés) :
```java
private SearchResponse<TravelDocument> searchResponseWithIds(UUID... ids) {
    List<Hit<TravelDocument>> hits = java.util.Arrays.stream(ids)
            .map(id -> new Hit.Builder<TravelDocument>()
                    .index("travels")
                    .id(id.toString())
                    .build())
            .toList();

    return new SearchResponse.Builder<TravelDocument>()
            .took(1)
            .timedOut(false)
            .shards(s -> s.total(1).successful(1).failed(0))
            .hits(h -> h.hits(hits))
            .build();
}
```
`Hit.Builder` exige `index`+`id` ; `HitsMetadata.Builder` exige `hits` (liste non vide) ; `SearchResponse.Builder` (via sa classe mère `ResponseBody.AbstractBuilder`) exige `took`+`timedOut`+`shards`+`hits` ; `ShardStatistics.Builder` exige `total`+`successful`+`failed`.

**À retenir** : sur ce projet, un client externe fortement typé comme `elasticsearch-java` mélange des interfaces (mockables sans souci, ex. `ElasticsearchClient`) et des classes concrètes générées avec constructeur privé (non mockables proprement, ex. `Hit`/`HitsMetadata`/`SearchResponse`) — pour ces dernières, construire de vraies instances via leurs Builders publics plutôt que de les mocker. Plus généralement : face à un échec de test qui persiste après un premier correctif non vérifié par une preuve concrète, ne pas tenter un 2e correctif à l'aveugle — lire le rapport Surefire complet sur disque (la sortie console collée dans un terminal peut être tronquée) avant de reformuler une hypothèse.

## 21. SonarQube signale `RecommendationSyncService.recommend()` : "Change this condition so that it does not always evaluate to 'false'"

**Problème** : Quality Gate en échec sur `lets-travel-travel-service`, un seul New Code issue restant après la correction des round précédents : `ids == null` dans `recommend()` jugé toujours faux par l'analyse symbolique de Sonar.

**Cause** : `recommendationRepository.recommendTravelIds(...)` est une méthode Spring Data qui retourne une `List` — Spring Data garantit qu'une telle méthode ne renvoie jamais `null` (liste vide sinon), une convention documentée que Sonar connaît via les annotations de null-safety Spring (package `org.springframework.transaction.support`). Le check défensif `ids == null ? List.of() : ...` était donc du code mort, provable comme tel par Sonar.

**Solution** : suppression du check et du test associé (`recommendReturnsEmptyListWhenRepositoryReturnsNull`, qui mockait un cas que Spring Data ne peut pas produire) :
```java
public List<UUID> recommend(UUID travelerId) {
    List<String> ids = neo4jTransactionTemplate.execute(status ->
            recommendationRepository.recommendTravelIds(travelerId.toString(), DEFAULT_RECOMMENDATION_LIMIT));
    return ids.stream().map(UUID::fromString).toList();
}
```

**À retenir** : sur ce projet, ne pas ajouter de garde `== null` par réflexe autour d'un retour de méthode Spring Data de type collection — c'est à la fois inutile (contrat jamais violé) et repéré comme code mort par Sonar. Confirmé par un `mvn test` vert (158/158) après suppression.

## 22. Le stage Deploy échoue à créer le conteneur `elasticsearch` : "sysctl vm.max_map_count is not in a separate kernel namespace"

**Problème** : build+test+Sonar passent sur `lets-travel-travel-service`, mais le stage Deploy plante à la création du conteneur `elasticsearch` : `OCI runtime create failed: runc create failed: sysctl "vm.max_map_count" is not in a separate kernel namespace: unknown`.

**Cause** : le service `elasticsearch` définissait `sysctls: - vm.max_map_count=262144` dans `docker-compose.yml`. Or `vm.max_map_count` est un sysctl kernel **global**, jamais isolable par conteneur (confirmé par l'issue officielle `docker/compose#4498`) — ce mécanisme ne peut structurellement jamais fonctionner, quel que soit l'hôte Docker.

**Solution** : suppression du `sysctls:`. Aucun contournement n'était nécessaire : `discovery.type: single-node` (déjà configuré sur ce service) place Elasticsearch en **mode développement** (doc Elastic officielle + commit `elastic/elasticsearch@5b7fd72`), où un bootstrap check en échec — dont `vm.max_map_count` — devient un simple warning au démarrage plutôt qu'un blocage. Le nœud démarre normalement, avec juste un warning dans ses logs.

**À retenir** : sur ce projet, un sysctl kernel global ne doit jamais être défini via `sysctls:` dans `docker-compose.yml` (échoue selon l'hôte/kernel). Avant d'ajouter une solution de contournement (fichier de config hôte, conteneur privilégié...), vérifier si la configuration déjà en place (ici `discovery.type=single-node`, service jamais exposé à l'extérieur) ne rend pas le problème inoffensif par défaut. Confirmé par un pipeline Jenkins vert (build+test+Sonar+Deploy) et le merge de `feat/search-and-recommendations` dans `main`.

## 23. Quality Gate frontend en échec sur `lets-travel-frontend` (PR `feat/traveler-frontend`) : assertion générique `.length).toBe(n)` au lieu de `toHaveLength(n)`

**Problème** : le job Sonar `npx --yes @sonar/scan` du pipeline PR-9 échoue (`QUALITY GATE STATUS: FAILED`) sur un seul New Code issue, Low, règle "Prefer a more specific assertion instead of this generic one" — `payment-method-form.spec.ts:98`, `expect(fixture.componentInstance['users']().length).toBe(1)`.

**Cause** : même règle Sonar déjà rencontrée sur `feat/manager-frontend` (`troubleshooting.md`, incident non numéroté du round 1 Sonar de cette branche, 9 occurrences à l'époque) — un `expect(x.length).toBe(n)` doit s'écrire `expect(x).toHaveLength(n)`, plus lisible et donnant un message d'échec plus précis. Cette occurrence a été introduite dans un test écrit lors de la passe préventive Sonar du 25/08 sur `feat/traveler-frontend` : le grep systématique de cette passe couvrait S1192, les imports dupliqués/inutilisés et le mort-code Spring Data, mais pas ce pattern déjà rencontré — oubli à corriger dans la checklist de vérification préventive.

**Solution** :
```typescript
// avant
expect(fixture.componentInstance['users']().length).toBe(1);
// après
expect(fixture.componentInstance['users']()).toHaveLength(1);
```

**À retenir** : ajouter systématiquement un grep `\.length\)\.toBe\(|\.length\)\.toEqual\(` sur tous les fichiers `*.spec.ts` neufs/modifiés lors de toute passe préventive Sonar sur ce projet — cette règle a déjà coûté un aller-retour CI à deux reprises (`feat/manager-frontend` puis `feat/traveler-frontend`). **Confirmé résolu** : Quality Gate frontend passée au round CI suivant.

## 24. Stage Deploy : `rm: cannot remove '.../backend/user-service/target': Directory not empty`

**Problème** : le round CI suivant (build+test+Sonar passés) plante au tout début du stage Deploy sur `rm -rf "$DEPLOY_DIR"/*` (script de préservation des secrets Vault/certs avant reconstruction du workspace de déploiement), avec `rm: cannot remove '.../deploy-workspace/backend/user-service/target': Directory not empty`.

**Cause probable** : `target/` (avec `classes/`, `test-classes/`, `generated-sources/`, `generated-test-sources/` dedans) est un résidu qui n'a structurellement pas pu être créé par ce pipeline — aucune tâche Ansible n'appelle Maven, le `mvn verify` du stage Build & Test tourne dans le checkout Jenkins (`$WORKSPACE`) et jamais dans `deploy-workspace`, `tar` l'exclut explicitement à la reconstruction, et le service `user-service` du `docker-compose.yml` ne bind-monte pas de dossier source/target dans son conteneur. Reste donc une cause externe au pipeline : très probablement un IDE (ex. IntelliJ) ayant ouvert/indexé `infra/ci/deploy-workspace` comme projet Maven et compilé en arrière-plan, ou un `./mvnw` lancé à la main directement dans ce dossier plutôt que dans le vrai repo — `deploy-workspace` est entièrement gitignored donc rien n'empêche ce genre de manipulation accidentelle. Le blocage de suppression lui-même est cohérent avec un handle de fichier Windows encore ouvert (compilateur/IDE) au moment du `rm -rf`, contrairement à Linux qui autorise l'unlink d'un fichier ouvert. Cause non confirmée à 100% (pas d'accès à l'hôte/IDE de l'utilisateur depuis cet environnement pour l'observer en direct).

**Solution** : le `rm -rf "$DEPLOY_DIR"/*` du `Jenkinsfile` est encapsulé dans une boucle de 5 tentatives avec 3s de pause entre chacune, plutôt qu'un `set -e` qui ferait échouer tout le stage sur un verrou transitoire :
```bash
for attempt in 1 2 3 4 5; do
    rm -rf "$DEPLOY_DIR"/* && break
    [ "$attempt" = 5 ] && { echo "rm -rf $DEPLOY_DIR/* a echoue apres 5 tentatives" >&2; exit 1; }
    sleep 3
done
```
Solution portable (dans le `Jenkinsfile` versionné, pas une bidouille locale). Le dossier `target/` bloquant a aussi été signalé à l'utilisateur pour suppression manuelle ponctuelle, la boucle de retry ne pouvant pas garantir qu'un verrou déjà posé au moment du run se libère en quelques secondes.

**À retenir** : sur ce projet, tout `rm -rf` sur un chemin Windows monté via WSL2/DrvFs dans un script CI doit être encapsulé dans une boucle de retry courte plutôt que de faire échouer tout le stage sur un premier échec — cohérent avec les autres soucis de verrous transitoires déjà rencontrés sur ce repo (nginx, `internal.crt`). Éviter d'ouvrir un IDE ou de lancer une commande directement dans `infra/ci/deploy-workspace` (entièrement gitignored, reconstruit à chaque run) — un projet Maven importé là par erreur peut laisser un `target/` verrouillé. **Confirmé résolu** : stage Deploy passé au round CI suivant, pipeline entièrement vert.


## 25. Formulaires de paiement frontend restés désynchronisés d'un changement de contrat backend déjà ancien (`amount`/`currency`)

**Problème** : en creusant le point "amount/currency ignorés côté backend" pendant l'audit `fix/audit-gaps`, découverte que `payment-service`'s `PaymentRequest` avait déjà retiré `amount`/`currency` (le montant vient de `travel-service`, jamais du client — commentaire explicite dans le fichier) il y a plusieurs branches, mais DEUX formulaires Angular (`TravelDetail` côté Traveler et `PaymentForm` côté Admin) envoyaient encore ces deux champs en les laissant modifiables par l'utilisateur, sans le moindre effet réel (Spring ignore silencieusement les champs JSON inconnus d'un DTO, il ne les rejette pas).

**Cause** : même famille que les points 14/15 — un changement de contrat backend non propagé au frontend, invisible faute de test e2e frontend↔backend et parce qu'aucune erreur ne se produit (le champ en trop est juste ignoré, pas rejeté en 400).

**Solution** : retrait de `amount`/`currency` du modèle `PaymentRequest` frontend et des deux formulaires, remplacés par un affichage en lecture seule du prix réel du voyage sélectionné (`travel.price`/`travel.currency`).

**À retenir** : un champ de DTO retiré côté backend doit être recherché explicitement côté frontend (`grep` du nom du champ) — contrairement à un champ ajouté et devenu obligatoire (qui casse bruyamment en 400, cas des points 14/15), un champ retiré ne casse jamais rien silencieusement, il reste juste inutile et trompeur pour l'utilisateur jusqu'à ce que quelqu'un le remarque en lisant le code des deux côtés.

## 26. `NG0203` : `takeUntilDestroyed()` appelé hors contexte d'injection depuis une méthode invoquée par `ngOnInit`

**Problème** : en branchant l'autocomplete Elasticsearch sur `TravelBrowse` (`fix/audit-gaps`), `ng test` échoue avec `NG0203: takeUntilDestroyed() can only be used within an injection context` — l'appel se trouvait dans une méthode privée (`watchAutocomplete()`) elle-même appelée depuis `ngOnInit()`, pas directement dans le constructeur/field initializer du composant.

**Cause** : `takeUntilDestroyed()` sans argument résout son `DestroyRef` via `inject()` en interne, qui exige d'être appelé de façon synchrone pendant la construction du composant (constructeur, field initializer, ou une fonction factory) — un appel depuis `ngOnInit()`, même synchrone à l'exécution, n'est plus dans cette fenêtre d'injection implicite dès qu'il passe par une méthode intermédiaire.

**Solution** : injecter `DestroyRef` explicitement en field initializer (`private readonly destroyRef = inject(DestroyRef);`) et le passer en argument : `takeUntilDestroyed(this.destroyRef)`.

**À retenir** : sur ce projet, tout usage de `takeUntilDestroyed()` en dehors d'un appel direct dans le corps du constructeur ou d'un field initializer doit passer un `DestroyRef` injecté explicitement — ne jamais compter sur la résolution implicite dès que l'appel est fait depuis `ngOnInit()` ou toute méthode appelée après la construction.

## 27. Un `router.navigate()` réellement déclenché dans un test avec `provideRouter([])` fait échouer des fichiers de test sans rapport

**Problème** : en ajoutant un test qui va jusqu'au bout d'un flux delete (`TravelForm`, `fix/audit-gaps`) — flush de la requête HTTP puis `router.navigate(['/travels'])` réellement exécuté — `ng test` faisait échouer en cascade plusieurs fichiers de test sans rapport (`app.spec.ts`, `manager-public.spec.ts`, `manager-travel-detail.spec.ts`) avec `Cannot configure the test module when the test module has already been instantiated`, en plus d'un `NG04002: Cannot match any routes` listé séparément comme "Unhandled Rejection".

**Cause** : les tests existants du projet ne laissaient jamais un flux `submit()`/`delete()` aller jusqu'au `router.navigate(...)` final avec `provideRouter([])` (aucune route enregistrée) — la navigation levait `NG04002` de façon asynchrone, après la fin du test lui-même, sous forme de rejet de promesse non intercepté qui pouvait corrompre l'exécution d'un fichier de test suivant dans le même worker Vitest.

**Solution** : enregistrer une route factice pour la cible de la navigation (`provideRouter([{ path: 'travels', component: DummyComponent }])`, `DummyComponent` étant un composant vide déclaré dans le fichier de test) — pattern déjà utilisé par `login.spec.ts` pour le même besoin.

**À retenir** : dès qu'un test de composant sur ce projet laisse un flux aller jusqu'à un `router.navigate(...)` réel (pas juste vérifié par un spy), `provideRouter([])` ne suffit pas — il faut enregistrer au moins une route factice pour la cible, sous peine d'un rejet de promesse non intercepté qui peut faire échouer des fichiers de test totalement sans rapport exécutés dans le même worker.

## 28. Certificat TLS interne correct sur disque, mais 500 sur toute requete inter-services (login, inscription) — bind mount Docker Desktop/WSL2 perime

**Probleme** : `POST /api/auth/login` et `/api/auth/register` renvoyaient systematiquement 500. Le log `api-gateway` montrait la vraie cause : `CertificateException: No subject alternative DNS name matching lets-travel-app-auth-service-1 found.` Pourtant `openssl x509 -in infra/internal-tls/certs/internal.crt -noout -text` confirmait que le certificat sur disque contenait bien ce SAN (ainsi que tous les autres noms de conteneurs attendus) — la config Ansible (`ansible/playbooks/deploy.yml`) est correcte et n'a pas bouge.

**Cause** : les conteneurs `auth-service`/`api-gateway` etaient deja demarres (ou leur bind mount deja etabli) avant que le fichier `internal.crt` ne soit (re)genere sur le disque hote. Docker Desktop sur WSL2 peut servir une vue perimee d'un bind mount a un conteneur deja lance — meme bug de fond que celui deja documente pour `vault-init` dans `deploy.yml` (commentaire : "reusing a stopped one can hit a stale Docker Desktop/WSL2 bind-mount bug"), ici sur un montage different (`./infra/internal-tls/certs`).

**Solution immediate** : recreer entierement les conteneurs concernes pour qu'ils relisent le contenu actuel du bind mount — `docker compose down` puis `docker compose up -d` (un simple `restart` ne suffit pas forcement, il faut la recreation du conteneur).

**Solution definitive (pipeline)** : `docker compose up` seul ne recree jamais un conteneur juste parce qu'un fichier monte en bind mount a change sous ses pieds (seule la config declaree - env vars, image - est diffee). `deploy.yml` avait deja ce garde-fou pour `vault-init`/`postgres`/`neo4j`/`nginx` (task "Force-remove ... before recreate") mais pas pour les 5 microservices qui montent `infra/internal-tls/certs` : etendu a `auth-service`, `user-service`, `travel-service`, `payment-service`, `api-gateway` dans la meme task. En passant systematiquement par le pipeline Ansible (`ansible-playbook ... site.yml`, meme commande que Jenkins) plutot que par un `docker compose up` direct, ce bind-mount perime ne peut plus se reproduire sans intervention manuelle.

**À retenir** : sur ce projet et cet environnement (WSL2), tout bug de type "le fichier sur disque est correct mais le conteneur ne le voit pas" doit faire suspecter en premier ce bind-mount perime avant de re-suspecter la config elle-meme. Et surtout : ce garde-fou doit couvrir TOUS les services qui montent un fichier regenerable, pas seulement ceux touches par le premier incident (vault-init) - la meme classe de bug peut resurgir sur n'importe quel autre bind mount tant que le service concerne n'est pas dans la liste de force-remove.

## 29. Redirection HTTP→HTTPS nginx cassee quand les ports host ne sont pas 80/443

**Probleme** : suivre un lien `http://localhost:8080/...` redirigeait vers une URL `https://localhost/...` qui ne repondait pas, obligeant a corriger l'URL a la main (port 8443 manquant).

**Cause** : `infra/nginx/nginx.conf` redirigeait avec `return 301 https://$host$request_uri;` — la variable nginx `$host` ne contient jamais le port. Comme le projet mappe volontairement `8080`/`8443` (pas `80`/`443`, pour tourner en parallele d'une autre instance), la redirection retombait sur le 443 implicite, non mappe cote host.

**Solution** : port HTTPS explicite dans la redirection (`https://$host:8443$request_uri`), avec un commentaire pointant vers `NGINX_HTTPS_HOST_PORT` si ce port est change dans `.env`.

**À retenir** : `$host` seul ne suffit jamais pour une redirection HTTP→HTTPS des que les ports exposes ne sont pas les ports standards 80/443.

## 30. Formulaires Login/Register : aucun retour visuel si le formulaire est invalide a la soumission

**Probleme** : soumettre le formulaire d'inscription (ou de login) avec un champ obligatoire manquant ne produisait absolument aucun retour utilisateur — ni message d'erreur, ni style, ni log console. Difficile a distinguer d'un vrai bug backend au premier abord.

**Cause** : `submit()` faisait `if (this.form.invalid) { this.form.markAllAsTouched(); return; }` sans jamais renseigner le signal `errorMessage` deja utilise et affiche par ces deux templates pour les erreurs HTTP — `markAllAsTouched()` seul ne suffit pas en l'absence de styles/messages par champ bases sur l'etat `touched+invalid`.

**Solution** : `errorMessage.set(...)` ajoute dans la branche invalide de `Login.submit()` et `Register.submit()`, reutilisant le paragraphe d'erreur deja present dans les deux templates.

**À retenir** : un signal d'erreur affiche seulement sur les erreurs HTTP (`error: (error) => ...`) laisse un trou silencieux sur le chemin de validation locale (`form.invalid`) si personne n'y pense explicitement — verifier les deux chemins a chaque nouveau formulaire.

## 31. Register : message d'erreur present mais toujours pas exploitable - un champ invalide invisible (password trop court, masque)

**Probleme** : formulaire d'inscription rempli en apparence dans tous les champs, soumission qui echoue quand meme avec le message generique "Certains champs obligatoires sont manquants ou invalides" (entree #30) - impossible de savoir quel champ pose probleme, en particulier `password` (masque par `type="password"`, sa longueur invisible a l'oeil).

**Cause** : `register.html` n'avait aucun `[class.invalid]` sur ses champs (contrairement a son propre voisin `login.html`, qui a exactement ce binding sur `username`/`password`) - un champ invalide ne se distingue donc visuellement d'aucune facon des autres. Concretement ici : `password` a `Validators.minLength(6)`, et un mot de passe de moins de 6 caracteres ne montre aucun signe (pas de bordure rouge, pas d'indice de longueur requise).

**Solution** : `[class.invalid]="form.controls.X.invalid && form.controls.X.touched"` ajoute sur les 9 champs requis de `register.html` (5 principaux + 4 de l'adresse), meme pattern que `login.html` deja etabli. Indice permanent "6 caracteres minimum" ajoute sous le champ password, seul champ dont la contrainte est invisible meme une fois le champ rempli.

**À retenir** : un nouveau formulaire doit reprendre le binding `[class.invalid]` de ses voisins des sa creation, pas seulement le signal `errorMessage()` (entree #30) - un message generique sans mise en evidence du champ fautif reste quasi inexploitable des que le formulaire depasse 2-3 champs.

## 32. 401 sur `/api/auth/register` et `/api/users/register` malgre un `permitAll()` correct cote service

**Probleme** : inscription impossible en conditions reelles (branche `fix/audit-gaps`, verification finale avant oral) - `POST /api/users/register` renvoyait 401 avant meme d'atteindre `user-service`, alors que `SecurityConfig` de ce service autorise explicitement cette route en `permitAll()`.

**Cause** : `api-gateway/RouteConfig.java` appliquait `jwtFilter` sans exception a toutes les routes `/api/users/**` et `/api/auth/**` (sauf `/api/auth/login`, deja isolee). Le filtre JWT de la gateway rejette une requete sans token avant meme qu'elle atteigne le microservice cible - la regle `permitAll()` de `user-service`/`auth-service` n'a donc jamais l'occasion de s'appliquer, la gateway bloquant en amont.

**Solution** : `RouteConfig.java` scinde desormais chaque route de service en deux `RouterFunction` distincts - un groupe `*-public` (sans `jwtFilter`) pour `/api/auth/login`, `/api/auth/register` et `/api/users/register`, un groupe protege (`jwtFilter` applique) pour tout le reste.

**À retenir** : sur une architecture a gateway, une route publique doit etre `permitAll()` a la fois cote microservice ET cote gateway (routing/filtres) - les deux couches font de l'auth independamment, et la premiere qui bloque a raison.

## 33. `/api/auth/me` reserve a ADMIN empeche tout Traveler/Manager de recuperer son propre profil apres login

**Probleme** : trouve lors de l'audit complet (pas encore reporte par un utilisateur) - `SecurityConfig` d'`auth-service` ne declarait aucune regle explicite pour `/api/auth/me`, route appelee par tous les roles juste apres login/register pour recuperer l'identite du compte connecte.

**Cause** : en l'absence de regle dediee, `/api/auth/me` tombait dans le `anyRequest().hasRole("ADMIN")` final - seul un compte ADMIN pouvait donc s'authentifier lui-meme via cette route, ce qui aurait bloque tout Traveler/Manager en aval du login des que le frontend l'appelle.

**Solution** : ajout d'une regle explicite `.requestMatchers("/api/auth/me").authenticated()` avant le `anyRequest().hasRole("ADMIN")`, ainsi qu'un `permitAll()` explicite sur `/api/auth/register` (deja public via la gateway mais sans regle propre cote service).

**À retenir** : `anyRequest()` en regle de secours (fallback) cache facilement des routes appelees par tous les roles qui n'ont jamais recu de regle dediee - toute nouvelle route consommee par plusieurs roles doit avoir sa propre ligne explicite, jamais compter sur le fallback pour deviner l'intention.

## 34. 500 intermittent sur login qui persiste apres le fix TLS, meme sur un compte cree avec le bon mot de passe

**Probleme** : apres correction du bug de bind-mount TLS (entree #28), le login restait intermittemment en 500 malgre un mot de passe correct sur un compte fraichement cree.

**Cause** : `AdminSeeder` (execute au demarrage de chaque replica `auth-service`, `deploy.replicas: 2`) tente de creer le compte admin par defaut sans protection contre la concurrence. Le force-remove ajoute en #28 fait qu'un `docker compose up` recree desormais systematiquement les deux replicas ensemble a chaque deploiement - ils demarrent alors en meme temps, et le second `accountRepository.save(admin)` leve une `DataIntegrityViolationException` non rattrapee dans le `CommandLineRunner`, ce qui fait planter la JVM de ce replica. Le replica redemarre (`restart: unless-stopped`) mais pendant la fenetre de crash/redemarrage, le load balancer statique de la gateway (`SimpleDiscoveryClient`, sans connaissance de l'etat de sante) continue de router une partie du trafic - dont des logins legitimes - vers l'instance en train de planter.

**Solution** : `accountRepository.save(admin)` entoure d'un `try/catch(DataIntegrityViolationException)` dans `AdminSeeder` - le second replica log simplement qu'un autre replica a deja cree l'admin, au lieu de planter.

**À retenir** : tout `CommandLineRunner`/code de bootstrap qui ecrit en base doit etre idempotent et tolerant a la concurrence des que le service tourne en plusieurs replicas - un simple `count() == 0` avant insertion (deja en place ici) ne protege pas contre une course entre deux instances qui demarrent au meme instant. Gap connu, non traite faute de temps/perimetre : la gateway n'a pas de load balancing conscient de l'etat de sante (pas de retrait automatique d'un replica en train de planter/redemarrer).

## 35. `auth-service` seul service sans gestion complete des exceptions - un 500 sur exception inattendue ne donnait aucune information exploitable

**Probleme** : trouve lors de l'audit complet - `auth-service` etait le seul des 5 microservices sans `ApiExceptionHandler` couvrant `DataIntegrityViolationException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException` et `MethodArgumentTypeMismatchException` ; une exception dans ce perimetre remontait en 500 brut sans message exploitable ni log structure, contrairement aux autres services deja audites.

**Solution** : `ApiExceptionHandler.java` d'`auth-service` complete avec les memes handlers que `user-service` (409 message explicite sur doublon, 400 sur validation/JSON malforme/type invalide, 500 avec `log.error` sur le reste), en conservant le format `ErrorResponse{message}` deja utilise par ce service plutot que de changer de format.

**À retenir** : une gestion d'erreur consistante doit etre verifiee service par service et pas seulement teste implicitement via les flux qui marchent - un service qui gere bien ses erreurs "connues" peut quand meme laisser un angle mort sur les erreurs generiques (JSON malforme, contrainte DB) si personne ne l'a copie depuis le pattern etabli ailleurs dans le projet.

## 36. `payment-service` sans truststore TLS interne - risque de `SSLHandshakeException` sur ses appels sortants vers `travel-service`

**Probleme** : trouve lors de l'audit infra complet, pas encore reproduit en conditions reelles - `payment-service/application-docker.properties` ne declarait pas de truststore pour le bundle SSL interne (`spring.ssl.bundle.pem.internal-services`), contrairement a `api-gateway` qui a la meme ligne.

**Cause** : sans ce truststore explicite, le `RestClient` sortant de `payment-service` vers `travel-service` retombe sur le `cacerts` par defaut de la JVM, qui ne connait pas le certificat auto-signe genere par le pipeline (`infra/internal-tls/certs/internal.crt`) - tout appel sortant echouerait en `SSLHandshakeException` des que ce chemin de code serait exerce.

**Solution** : ajout de `spring.ssl.bundle.pem.internal-services.truststore.certificate=file:/etc/internal-tls/internal.crt`, identique a la ligne deja presente cote `api-gateway`.

**À retenir** : toute config TLS interne (bundle keystore/truststore) doit etre dupliquee de facon identique sur TOUS les services qui font des appels sortants vers un autre service interne, pas seulement ceux ou le bug a deja ete vu - verifier par recherche croisee (`grep` du nom de la propriete sur tous les `application-docker.properties`) plutot que service par service au fil des rapports de bug.

## 37. `vault` non couvert par le force-remove anti bind-mount perime (meme classe de bug que #28, sur un autre service)

**Probleme** : trouve lors de l'audit infra complet - le service `vault` monte lui-meme des fichiers regenerables (`infra/vault/certs`, config), exactement le meme type de montage que celui qui causait le bug TLS de l'entree #28, mais n'etait pas dans la liste de force-remove etendue a cette occasion (qui ne couvrait que `postgres`, `neo4j`, `nginx` et les 5 microservices).

**Solution** : `vault` ajoute a la meme liste de force-remove dans `deploy.yml` (task renommee en consequence, variable `stale_mount_containers_rm`).

**À retenir** : suite directe de l'À retenir de l'entree #28 - cette liste doit etre revue a chaque nouveau service qui monte un fichier regenerable en bind mount, `vault` avait ete oublie car protege par ailleurs par sa propre tache "Force-remove any previous vault-init container" qui ne couvre que le conteneur d'init, pas `vault` lui-meme.

## 38. IDOR sur `GET /api/users/{id}` - un Travel Manager peut recuperer le profil complet de N'IMPORTE QUEL utilisateur, pas seulement celui d'un de ses abonnes

**Probleme** : trouve lors de l'audit complet (verification point par point de `lets-travel_audit.md`, ligne "role-based access controls correctly enforced") - un compte TRAVEL_MANAGER authentifie pouvait appeler `GET /api/users/{id}` avec l'UUID de N'IMPORTE QUEL utilisateur de la plateforme (traveler ou meme autre manager) et recevoir son profil complet (email, telephone, adresse...), sans aucune relation d'abonnement entre les deux comptes.

**Cause** : `SecurityConfig` de `user-service` autorisait deja correctement la route au niveau HTTP (`.hasAnyRole("ADMIN", "TRAVEL_MANAGER")`), mais ce controle de ROLE etait le seul en place - `UserService.findById` renvoyait le profil demande a n'importe quel appelant ayant l'un de ces deux roles, sans jamais verifier que le TRAVEL_MANAGER appelant avait une RELATION legitime (un abonnement a l'un de ses voyages) avec le `travelerId` cible. Un role autorise a consulter des profils n'implique pas qu'il soit autorise a consulter TOUS les profils.

**Solution** : ajout cote `travel-service` d'un endpoint interne `GET /api/travels/managers/me/subscribers/{travelerId}` (`ManagerStatsController` + `ManagerStatsService.isMySubscriber`, verifie via `SubscriptionRepository.existsByTravel_ManagerIdAndTravelerId`) qui repond si le traveler cible est abonne a l'un des voyages du manager appelant. Cote `user-service`, `UserService.findById` prend desormais `callerIsAdmin` + le header `Authorization` d'origine, et appelle ce nouvel endpoint via un `TravelServiceClient` (meme pattern que le client `payment-service` -> `travel-service` deja en place : `RestClient` charge-balance via `spring-cloud-starter-loadbalancer`, mTLS sortant via le bundle `internal-services`, propagation du JWT, fail-closed sur toute erreur reseau/HTTP) : ADMIN passe toujours, TRAVEL_MANAGER doit avoir une relation d'abonnement confirmee sinon `ForbiddenException` (403). Necessite l'ajout du BOM `spring-cloud-dependencies` dans le `pom.xml` d'`user-service` (absent jusqu'ici) et deux nouvelles variables d'environnement `TRAVEL_SERVICE_URI_1`/`TRAVEL_SERVICE_URI_2` dans `docker-compose.yml`, identiques a celles deja utilisees par `payment-service`.

**À retenir** : `hasAnyRole(...)` a la couche HTTP (Spring Security) verifie uniquement que l'appelant POSSEDE un role autorise - jamais qu'il a le droit sur CETTE ressource precise. C'est un IDOR classique des qu'une route parametree par un ID est ouverte a un role "manager/gestionnaire" sans second controle au niveau service. Toute route de ce type doit imposer une verification explicite de relation (ownership/abonnement/appartenance) cote service, en plus - jamais a la place - du controle de role cote securite HTTP. A verifier systematiquement sur toute autre route exposee a TRAVEL_MANAGER : le controle "role-based access controls correctly enforced" de l'audit ne testait que la premiere moitie du probleme.

## 39. `api-gateway` ne fait confiance a AUCUN certificat interne pour ses appels sortants - toute requete routee (login, register, tout le reste) plante en 500

**Probleme** : decouvert en essayant de verifier manuellement le fix IDOR (#38) de bout en bout via `docker compose up` complet (nginx + tous les microservices, pas de mock) - TOUTE requete passant par `api-gateway` vers un service en aval (a commencer par `POST /api/auth/login`) echouait en 500, sans lien avec le fix IDOR lui-meme. Bug d'infrastructure pre-existant, jamais decouvert avant car le projet n'avait apparemment jamais ete teste de bout en bout via la stack docker-compose complete (nginx + mTLS reel) - les tests unitaires/integration mockent la couche reseau/TLS.

**Cause** : `RouteConfig` proxie chaque service via `HandlerFunctions.http()` + le filtre `lb(...)` de `spring-cloud-gateway-server-webmvc`, qui recuperent tous les deux un bean `RestClient.Builder` dans le contexte Spring. `api-gateway` n'en definissait aucun explicitement, donc gateway-server-webmvc construisait son propre `RestClient.Builder` par defaut - qui n'utilise pas le bundle mTLS `internal-services` (les proprietes `spring.ssl.bundle.pem.internal-services.*` d'`application-docker.properties` ne s'appliquent qu'aux clients HTTP auto-configures directement par Spring Boot, jamais a celui-la). Consequence : le JVM retombe sur son cacerts par defaut, qui ne fait pas confiance au certificat auto-signe interne -> `PKIX path building failed: unable to find valid certification path to requested target` sur CHAQUE appel sortant du gateway.

**Solution** : ajout de `GatewayHttpClientConfig` (meme pattern que `TravelServiceClientConfig` de payment-service/user-service, `troubleshooting.md` #11) fournissant un bean `RestClient.Builder` dont le `JdkClientHttpRequestFactory` est construit avec le `SSLContext` du bundle `internal-services`. Deux pieges rencontres en cours de correction, tous les deux desormais commentes directement dans le code :
- **Pas de `@LoadBalanced` sur ce bean**, contrairement a `TravelServiceClientConfig`. Le filtre `lb(...)` deja present dans `RouteConfig` resout LUI-MEME le service logique (`auth-service`) vers une instance concrete (ex. `lets-travel-app-auth-service-2`) AVANT que `http()` n'appelle ce `RestClient`. Un bean `@LoadBalanced` a cet endroit tente de re-resoudre ce nom d'instance concret comme s'il s'agissait encore d'un service logique, et echoue avec `IllegalStateException: No instances available for lets-travel-app-auth-service-2` (double resolution - confirme par [spring-cloud/spring-cloud-gateway#3168](https://github.com/spring-cloud/spring-cloud-gateway/issues/3168), jamais documente clairement dans la doc officielle).
- **Le timeout de lecture doit etre dimensionne pour un usage generique** : un premier essai a 5s (copie de la valeur utilisee par `TravelServiceClientConfig` pour UN appel interne leger precis) a fait planter `POST /api/travels` (creation avec destinations imbriquees, plus lourde) en `HttpTimeoutException: Request cancelled` - passe a 30s, tres en dessous du defaut nginx (60s, aucun timeout explicite cote nginx non plus).

**À retenir** : ce bug etait invisible depuis le debut du projet car jamais teste bout-en-bout via la stack reelle (nginx + mTLS complet, pas de mock reseau) - un rappel qu'un test manuel via `docker compose up` complet reste necessaire pour attraper ce genre de trou de plomberie qu'aucun test unitaire ne peut voir, meme avec une suite de tests par service exhaustive (`mvn test` passait a 100% sur les deux services du fix IDOR pendant que l'API entiere etait injoignable en pratique). Plus specifiquement pour `spring-cloud-gateway-server-webmvc` : tout bean `RestClient.Builder` personnalise fourni pour cabler le client HTTP interne du gateway (mTLS, timeouts, etc.) ne doit PAS etre `@LoadBalanced` des lors que les routes utilisent deja le filtre `lb(...)` - les deux mecanismes de resolution entrent en conflit silencieusement.

## 40. 6 trous trouves lors de l'audit complet point-par-point (`lets-travel_audit.md`) - rate-limit login absent, headers de securite absents, index manquant, timeout paiement absent, 500 brut sur panne amont, traveler sans acces a son propre feedback/reports

**Probleme** : suite au fix du gateway (#39) et a la verification manuelle bout-en-bout du fix IDOR (#38), demande de verifier le projet EN ENTIER contre les ~30 points de `docs/lets-travel_audit.md`, pas seulement le point IDOR deja traite. Un audit en lecture seule (7 agents en parallele, un par domaine, aucune commande docker/mvn executee) a remonte 6 trous concrets et peu couteux a corriger, en plus de 3 fonctionnalites bonus entierement absentes (PWA, multilingue, fonctionnalite innovante - hors scope, decision explicite de Daro de ne pas les traiter).
Correction ulterieure : la conformite protection des donnees (RGPD) avait ete classee a tort dans ce meme lot de "bonus hors scope" - erreur signalee par Daro, qui a confirme que ce point ne figure ni dans la section Bonus de l'enonce ni dans celle de l'audit. Traitee separement et implementee le soir meme, voir #41.

### 40.1 Aucun rate-limit sur `POST /api/auth/login`

**Cause** : `auth-service` ne fait ni lockout ni delai progressif sur les echecs de connexion (verifie dans `AuthController`), et rien en amont (nginx) ne limitait non plus la frequence des requetes vers cette route - un brute-force ou credential-stuffing pouvait tourner sans aucune limite.

**Solution** : ajout d'une zone `limit_req_zone $binary_remote_addr zone=login_zone:10m rate=5r/m;` (nginx n'accepte cette directive qu'au niveau `http`, hors de portee du `infra/nginx/nginx.conf` existant qui est monte en `conf.d/default.conf` - creation d'un fichier `infra/nginx/nginx-main.conf`, replique du `nginx.conf` par defaut de l'image `nginx:1.27-alpine` avec cette seule ligne ajoutee, monte a la place du fichier par defaut via `docker-compose.yml`) puis un bloc `location /api/auth/login { limit_req_status 429; limit_req zone=login_zone burst=3 nodelay; ... }` place AVANT le `location /api/` generique dans `nginx.conf` (nginx retient le prefixe le plus long qui matche, l'ordre des blocs dans le fichier est indifferent mais le bloc specifique doit exister). `limit_req_status 429` explicite car par defaut nginx renvoie 503 quand le burst est depasse - ambigu (503 evoque un service en panne, pas un simple throttle), corrige en 429 ("Too Many Requests", RFC 6585) des la premiere ecriture pour eviter d'avoir a le redecouvrir plus tard en testant le script de verification.

### 40.2 Aucun header de securite HTTP (defense en profondeur XSS/clickjacking)

**Cause** : aucune reponse nginx n'envoyait `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Strict-Transport-Security` ni de `Content-Security-Policy` - le JWT est stocke en `localStorage` cote frontend (`auth.ts`), ce qui le rend volable par n'importe quel XSS si jamais un s'introduit malgre l'absence actuelle de faille connue.

**Solution** : ajout des 5 `add_header ... always;` dans le bloc `server { listen 443 ssl; }` de `nginx.conf`. La CSP reste permissive sur `script-src`/`style-src` (`'unsafe-inline'`) pour ne pas casser le build Angular actuel (non teste en usage strict) - a durcir si l'occasion se presente, mais ces headers ne changent rien au fonctionnement normal de l'app.

### 40.3 Colonne `travels.manager_id` sans index malgre un usage intensif en filtre

**Cause** : `manager_id` est le filtre principal de `ManagerStatsService`, des classements d'`AdminStatsService` (`managerRankings`/`travelRankings`) et de `TravelService.requireOwnershipOrAdmin`, mais n'avait jamais recu d'index - contrairement a `subscriptions.travel_id`/`traveler_id` (V3) et `feedbacks.travel_id`/`reports.travel_id`/`reported_id` (V5), qui ont le meme profil d'usage.

**Solution** : nouvelle migration Flyway `V6__add_index_travels_manager_id.sql` (`CREATE INDEX idx_travels_manager_id ON travels(manager_id);`), appliquee automatiquement au prochain demarrage de `travel-service`.

### 40.4 Client Stripe/PayPal sans aucun timeout

**Cause** : `PaymentProviderConfig.paymentRestClient()` utilisait `RestClient.create()` brut, qui n'a NI timeout de connexion NI timeout de lecture par defaut - contrairement a tous les appels internes du projet (`TravelServiceClientConfig`, `GatewayHttpClientConfig`, tous timeoutes), un fournisseur de paiement externe qui traine ou ne repond jamais aurait bloque la requete indefiniment, sans meme le filet de securite d'un timeout amont.

**Solution** : reconstruction du bean avec `JdkClientHttpRequestFactory` (meme pattern que `TravelServiceClientConfig`), `CONNECT_TIMEOUT=5s`, `READ_TIMEOUT=15s` (plus genereux que les appels internes docker de 2-3s, un fournisseur externe etant naturellement plus lent, mais reste borne).

### 40.5 `api-gateway` renvoie une 500 HTML brute (page Tomcat par defaut) si un service en aval est injoignable

**Cause** : sans gestionnaire d'exception global, toute panne totale d'un service en aval (ses 2 replicas down, timeout, ou tout autre echec du `RestClient` fourni par `GatewayHttpClientConfig`) remontait comme une 500 HTML brute par defaut, illisible et inexploitable par le frontend.

**Solution** : ajout d'`ApiExceptionHandler` (`@RestControllerAdvice`), meme pattern que celui deja en place dans `payment-service` - `RestClientException` (timeout, connexion refusee, TLS, erreur HTTP renvoyee par le service en aval) devient une 502 JSON structuree (`timestamp`/`status`/`error`/`message`), toute autre exception non prevue devient une 500 JSON. Les routes fonctionnelles de `RouterFunction` passent bien par le `DispatcherServlet`/`HandlerFunctionAdapter` standard (confirme dans la stack trace du bug #39), donc `@RestControllerAdvice` s'applique normalement.

### 40.6 Un Traveler ne peut pas relire le contenu de son propre feedback/reports deja soumis

**Cause** : `TravelerStatsResponse` (`mySubscriptions`) n'exposait que des COMPTEURS de feedbacks/reports, jamais leur contenu - et les endpoints qui exposent le contenu (`GET /api/travels/{travelId}/feedbacks`, `GET /api/reports`) sont reserves a ADMIN/TRAVEL_MANAGER. Un traveler n'avait donc aucun moyen de relire ce qu'il avait lui-meme ecrit.

**Solution** : ajout de `findByTravelerId`/`findByReporterId` (`FeedbackRepository`/`ReportRepository`), puis `myFeedbacks`/`myReports` dans `TravelerStatsService` (meme garde d'auto-restriction `requireTravelerId(caller)` que `mySubscriptions` - la cible est TOUJOURS `caller.userId()`, jamais un ID fourni par l'appelant), exposes via `GET /api/travels/travelers/me/feedbacks` et `GET /api/travels/travelers/me/reports` dans `TravelerStatsController`. Aucun changement `SecurityConfig` necessaire : deja couvert par la regle generique `GET /api/travels/**` -> TRAVELER minimum.

**À retenir** : ces 6 trous partagent un point commun - aucun n'a ete detecte par les tests unitaires/integration existants (qui passent tous a 100%), parce qu'aucun ne teste un COMPORTEMENT fonctionnel incorrect au sens strict (rien ne plante, rien ne renvoie le mauvais resultat) mais plutot une ABSENCE de protection/exposition qui ne se voit qu'en se posant explicitement la question "et si..." (et si on spamme le login, et si un service tombe, et si un traveler veut relire son propre avis...). Une suite de tests verte ne garantit jamais qu'un audit fonctionnel point-par-point n'a rien a redire - c'est exactement le role de la grille d'audit, distincte de `mvn test`. A verifier systematiquement pour tout futur endpoint : (1) y a-t-il un timeout explicite sur CHAQUE client HTTP sortant, (2) toute panne amont renvoie-t-elle une erreur JSON structuree ou une page brute, (3) un utilisateur peut-il relire/gerer TOUT ce qu'il a lui-meme cree, pas seulement ce qu'un role superieur peut voir sur lui.


## 41. Conformite protection des donnees (RGPD) absente - aucun consentement a l'inscription, aucun droit d'acces/portabilite, aucun droit a l'effacement self-service

**Probleme** : signale par Daro apres relecture de `docs/audit_reponses_detaillees.md`, qui classait ce point dans le meme lot que les 3 bonus hors scope (#40, intro) - erreur de classification corrigee sur place (voir la note ajoutee en tete de #40) : la protection des donnees personnelles ne figure dans la section Bonus ni de l'enonce ni de `lets-travel_audit.md`, c'est une exigence fonctionnelle a part entiere, absente du projet jusqu'ici. Aucune trace de consentement a l'inscription, aucun moyen pour un utilisateur de consulter/exporter ses propres donnees autrement qu'en recomposant a la main plusieurs appels `/me`, et aucun moyen de supprimer son propre compte (seul un ADMIN pouvait supprimer un profil via `DELETE /api/users/{id}`).

**Cause** : le projet n'avait jamais eu besoin de resoudre "qui suis-je" cote `auth-service`/`user-service` au-dela du username+role - `JwtAuthenticationFilter` des deux services ne mettait dans le principal Spring Security qu'une `String` (le username) et le role, jamais le `userId`, alors meme que le JWT porte deja ce claim depuis le debut (`auth-service.JwtService.generateToken`). Impossible donc de batir un endpoint self-service `/me` qui resout "mon propre profil" sans jamais recevoir d'id en parametre (le seul pattern sur, cf. le fix IDOR #38). Consequence secondaire decouverte en creusant : `UserService.delete(id)` (admin) supprimait le profil `user-service` mais ne touchait jamais au compte de connexion `auth-service` associe - un profil supprime par un ADMIN laissait derriere lui un compte "fantome" toujours capable de se reconnecter (`POST /api/auth/login` fonctionnait encore), aucun rapport avec la demande initiale mais un vrai bug corrige au passage.

**Solution** : implementation complete cote backend puis frontend, meme soir.
- **Principal enrichi** : `AuthenticatedUser(username, role, userId)` (implements `AuthenticatedPrincipal`) ajoute a `auth-service` ET `user-service` (seuls services sans ce pattern - `travel-service` l'avait deja), branche dans leurs `JwtAuthenticationFilter` respectifs. `auth-service.JwtService` avait deja `extractUserId` ; ajoute a `user-service.JwtService` qui ne lisait jamais ce claim.
- **Suppression cross-service reelle** : nouveau `DELETE /api/auth/accounts/by-user/{userId}` (`AccountController`, `auth-service`) avec la meme garde que le fix IDOR #38 (ADMIN OU proprietaire du `userId`, jamais un role seul), et `AuthServiceClient`/`AuthServiceClientConfig` cote `user-service` (reutilise le bean `@LoadBalanced RestClient.Builder` deja cable en mTLS par `TravelServiceClientConfig`, memes instances statiques que `travel-service` : `AUTH_SERVICE_URI_1`/`_2` -> `lets-travel-app-auth-service-1`/`-2:8081`). `UserService.delete(id, authorizationHeader)` supprime desormais le compte `auth-service` AVANT le profil local : si l'appel echoue, l'exception remonte et RIEN n'est supprime - jamais de profil supprime avec un compte fantome derriere (le bug cite plus haut est corrige pour le chemin ADMIN par le meme code que le nouveau chemin self-service).
- **Consentement a l'inscription** : `UserRegistrationRequest.acceptedPrivacyPolicy` (`@AssertTrue`, rejette l'inscription en 400 si la case n'est pas cochee), horodate dans `User.privacyAcceptedAt` (nouvelle colonne nullable, migration `V2__add_privacy_accepted_at.sql` - nullable car les profils crees par un ADMIN via `POST /api/users` n'ont pas ce consentement, ce qui est attendu). Case a cocher jamais cochee par defaut sur `register.html`, avec lien vers une nouvelle page publique `/politique-de-confidentialite`.
- **Droit d'acces/portabilite** : `GET /api/users/me` (nouveau, resout l'appelant via son principal, jamais un id) - reutilise `UserResponse` tel quel plutot qu'un DTO dedie, deja complet. Cote frontend, nouvelle page `/mon-compte` (visible aux 3 roles) qui affiche ce profil et propose un export JSON telechargeable en un clic (`Blob` + lien `download`, aucun aller-retour serveur supplementaire).
- **Droit a l'effacement** : `DELETE /api/users/me` (nouveau, self-service), meme chemin de suppression cross-service que `DELETE /api/users/{id}` (admin) ci-dessus. Cote frontend, meme page `/mon-compte`, avec le composant `ConfirmDialog` deja utilise ailleurs (`UserList`) plutot qu'une modale native - deconnexion et redirection `/login` immediates apres succes, le token local n'ayant plus aucune valeur (compte de connexion deja supprime a ce stade).

**Decision de perimetre assumee, pas cachee** : les donnees cross-service liees a un compte supprime (abonnements, feedbacks, reports, historique de paiement dans `travel-service`/`payment-service`) ne sont PAS purgees ce soir - elles restent en base, referencant un `travelerId`/`ownerId` qui ne pointe plus vers aucun profil (ces colonnes sont des UUID nus sans FK cross-service par conception, voir les commentaires existants dans le code - aucune infrastructure de cascade cross-service n'existe dans le projet). Cote utilisateur c'est un effacement reel (plus aucune trace nominative consultable), mais ce n'est pas un scrubbing ligne par ligne dans les 4 autres services. Signale explicitement a Daro en direct plutot que documente seul ici (voir echange du meme soir) - pas une omission.

**À retenir** : deux lecons distinctes. (1) Classer une exigence en "bonus" ou "hors scope" doit se verifier ligne par ligne contre l'enonce ET l'audit, jamais par supposition/analogie avec d'autres points effectivement bonus (PWA, i18n) traites dans le meme lot - une conformite legale (RGPD) n'est structurellement pas du meme ordre qu'une fonctionnalite "innovante". (2) Toute suppression de compte dans une architecture microservices sans FK cross-service doit etre orchestree explicitement cote application (ici : auth-service AVANT user-service, jamais l'inverse) - c'est exactement la meme classe de bug que le "compte fantome" trouve au passage : une suppression qui semble complete cote UI/API appelee mais qui laisse une trace ailleurs dans le systeme parce que personne n'a orchestre le nettoyage cross-service.


## 42. Faille de prise de controle de compte via `POST /api/auth/register` (userId non verifie) - aggravee par la suppression self-service RGPD (#41)

**Probleme** : trouve lors d'une re-verification adversariale complete de l'audit (8 agents en parallele, un par domaine fonctionnel/securite, chacun instruit de lire le code reel et de rester sceptique plutot que de faire confiance a la documentation existante), demandee par Daro apres avoir ete plusieurs fois deçu de decouvrir des trous plus tard malgre des affirmations anterieures de "c'est bon". `POST /api/auth/register` (2e etape de l'inscription publique traveler, feat/traveler-experience) acceptait un champ `userId` fourni tel quel par le CLIENT dans le corps de la requete, et le stockait sans aucune verification sur le nouveau compte de connexion (`Account.userId`). N'importe qui pouvait donc creer un compte de connexion (username/password de son choix) lie au `userId` de N'IMPORTE QUEL profil `user-service` existant - le sien, ou celui de quelqu'un d'autre, l'UUID etant devinable/enumerable (retourne par exemple dans les reponses `GET /api/users`, visible par tout ADMIN/TRAVEL_MANAGER, ou simplement brute-forcable vu le faible cout d'un essai). Une fois ce compte cree, se logger avec dessus donnait un JWT portant CE userId - soit un acces complet au profil de la victime a travers tous les endpoints qui font confiance a ce claim.

**Gravite fortement aggravee par #41** : avant l'ajout du self-service RGPD ce meme soir, le pire impact pratique de cette faille etait deja significatif (usurper le profil d'un traveler existant, lire ses donnees via les endpoints qui font confiance au JWT). Mais #41 a ajoute `GET /api/users/me` (lire) ET `DELETE /api/users/me` (supprimer, cross-service - profil ET compte de connexion) - tous deux resolvent leur cible EXCLUSIVEMENT depuis `caller.userId()` (le principal du JWT), jamais un id fourni en parametre, un choix delibere fait a l'epoque justement pour eviter tout IDOR explicite (cf. #38). Ce choix, correct en apparence, s'appuyait implicitement sur une hypothese non verifiee : qu'un JWT valide porte forcement le userId de son PROPRE detenteur legitime. La faille de `/api/auth/register` invalidait cette hypothese : un attaquant pouvait donc, en un seul appel non authentifie et sans jamais connaitre le mot de passe de la victime, obtenir un JWT valide portant le userId de CETTE victime, puis l'utiliser sur `GET /api/users/me` (lire son profil complet) et surtout `DELETE /api/users/me` (supprimer definitivement son profil ET son compte de connexion reel) - une prise de controle de compte complete, silencieuse, sans laisser a la victime la moindre chance de s'en apercevoir avant coup.

**Cause** : `AuthController.register` (avant fix) faisait confiance a `RegisterRequest.userId()` tel quel, sans jamais verifier que ce userId provenait effectivement d'un appel reussi et recent a `POST /api/users/register` (user-service). Aucun mecanisme ne liait cryptographiquement les deux etapes de l'inscription (creation du profil cote `user-service`, puis creation du compte de connexion cote `auth-service`) - le client etait le SEUL porteur de cette information entre les deux appels, et rien ne verifiait qu'il ne la falsifiait pas.

**Solution** : jeton de preuve d'inscription signe, court, a usage prevu unique, plutot qu'un userId en clair.
- `user-service.JwtService.generateRegistrationToken(UUID userId)` (nouvelle methode) signe un JWT avec la MEME cle partagee (Vault `shared/jwt`, confirmee identique dans les deux `JwtSigningKeyConfig`) que les JWT de connexion, mais avec un `subject` = userId (jamais un username, contrairement aux JWT de session) et un claim distinctif `"purpose":"user-registration"`, expiration courte (10 minutes). `UserService.register` l'appelle juste apres la sauvegarde du `User` et le renvoie dans une nouvelle reponse `RegistrationResponse(UserResponse user, String registrationToken)` (`POST /api/users/register` renvoie desormais cet objet au lieu du seul profil).
- `auth-service.JwtService.validateRegistrationToken(String token)` (nouvelle methode) verifie la signature, le claim `purpose`, la non-expiration, et extrait le userId du `subject` - leve `InvalidRegistrationTokenException` (400, via `ApiExceptionHandler`) sur tout echec (jeton absent, invente, expire, mal signe, ou sans le bon claim). `RegisterRequest` perd son champ `userId` au profit de `registrationToken` ; `AuthController.register` derive desormais le userId UNIQUEMENT de ce jeton verifie, jamais du corps de la requete.
- **Isolation du domaine de confiance** : le jeton de preuve n'a ni `role` ni `userId` en claim (contrairement a un JWT de session) et son `subject` est un UUID brut - si un tel jeton etait par erreur presente comme Bearer token ailleurs dans le systeme, `JwtAuthenticationFilter` resoudrait `role=null`/`userId=null` et echouerait toute autorisation, sans aucune confusion possible avec le circuit de connexion normal.
- **Defense en profondeur** : nouvelle migration `V3__add_accounts_user_id_unique_constraint.sql` (`ALTER TABLE accounts ADD CONSTRAINT uq_accounts_user_id UNIQUE (user_id)`) - meme si un jeton valide etait rejoue (fenetre de 10 minutes), un DEUXIEME compte de connexion ne peut plus jamais etre lie au meme userId (409, via le handler `DataIntegrityViolationException` deja en place). Postgres n'applique jamais l'unicite entre plusieurs `NULL`, donc le compte ADMIN par defaut (sans fiche `User`, `userId` null) n'est pas affecte.
- Frontend : `RegisterRequest.userId` -> `registrationToken` (`core/models/auth.ts`), nouvelle interface `RegistrationResponse` (`core/models/user.ts`), `UsersService.register` renvoie desormais `Observable<RegistrationResponse>`, et `Register.submit()` (`features/register/register.ts`) transmet `result.registrationToken` au lieu de `user.id` a la 2e etape.
- Tests : nouveaux tests directs (sans mock) sur `auth-service.JwtServiceTest` couvrant les 5 cas du nouveau `validateRegistrationToken` (jeton valide, sans claim purpose, expire, mauvaise cle, subject non-UUID) ; `AuthControllerTest` mis a jour (stub de `validateRegistrationToken`) plus un nouveau test dedie au rejet d'un jeton invalide ; `UserServiceTest`/`UserControllerTest` mis a jour pour le nouveau type de retour `RegistrationResponse` ; `verify_platform.sh` gagne un `TEST 11` dedie (inscription legitime acceptee, jeton invente rejete en 400, rejeu du meme jeton valide sous un autre username rejete en 409 par la contrainte UNIQUE) et `test_idor.sh` est mis a jour pour rester executable.

**À retenir** : une decision de conception qui semble fermer un IDOR ("resoudre uniquement depuis le principal du JWT, jamais un id fourni") ne vaut que ce que vaut la confiance accordee AU JWT lui-meme - si n'importe quel autre endpoint permet de faire emettre un JWT portant un claim choisi par l'attaquant, cette protection est illusoire. Toute donnee qui traverse une frontiere de confiance entre deux appels HTTP distincts (ici : userId communique par le client entre l'etape 1 - creation du profil - et l'etape 2 - creation du compte) doit etre soit revalidee independamment, soit transmise sous une forme que le client ne peut pas falsifier (ici : un jeton signe par le service qui a legitimement produit la donnee). Une fonctionnalite ajoutee plus tard (#41, le self-service RGPD) peut transformer une faille pre-existante de gravite moderee en faille critique sans que rien ne change dans le code de la faille elle-meme - un rappel qu'ajouter des capacites self-service (lire/modifier/supprimer "mon propre" compte) augmente mecaniquement l'enjeu de TOUT mecanisme qui etablit "qui je suis", meme des mecanismes anciens et jusque-la juges anodins.


## 43. 4 gaps trouves lors de la re-verification adversariale du 27/08 - recommandations Neo4j, dashboard admin, dashboard manager, contenu feedback/reports traveler

**Probleme** : suite a la demande de Daro de re-verifier point par point l'integralite de l'audit (8 agents en parallele, lecture de code reel), 4 gaps reels ont ete trouves en plus de la faille #42. Corriges le soir meme.

### 43.1 Neo4j ignorait la valeur de la note du feedback, seulement sa presence

**Cause** : `RecommendationRepository.recommendTravelIds` matchait tout voyage relie par `PARTICIPATED_IN` ou `RATED` de la meme facon - une note de 1/5 pesait exactement comme une note de 5/5 dans le calcul des recommandations.

**Solution** : chaque voyage aime pese desormais `note - 3` s'il a ete note (4-5/5 pese plus qu'une simple participation, 1-2/5 devient negatif et est exclu), ou 1 s'il n'a ete que participe sans note. Le score final est la somme de ces poids plutot qu'un simple compte.

### 43.2 Le dashboard admin ne proposait le lien feedback que pour les 5 premiers voyages du classement

**Cause** : `dashboard.html` tronquait `travelRankings()` a `slice: 0 : 5` cote frontend (le backend renvoie deja le classement complet) - un admin ne pouvait pas atteindre le feedback d'un voyage hors de ce top 5 depuis le dashboard.

**Solution** : suppression du `slice`, la table affiche desormais tous les voyages classes.

### 43.3 Le dashboard manager n'avait pas d'analyse par voyage

**Cause** : `ManagerStatsResponse` n'exposait que des totaux agreges (nombre de voyages, abonnes, revenu) - pas d'abonnes ni de note moyenne par voyage individuel.

**Solution** : nouveau champ `travels` (liste de `ManagerTravelStatsEntry` : id, titre, nombre d'abonnes actifs, note moyenne, nombre d'avis), calcule dans `ManagerStatsService.myStats`. Cote frontend, deux colonnes ajoutees a la table "mes voyages" du dashboard.

### 43.4 Le contenu du feedback/signalements d'un traveler etait accessible cote backend mais jamais affiche

**Cause** : `GET /api/travels/travelers/me/feedbacks` et `.../me/reports` existaient deja mais aucun code frontend ne les appelait.

**Solution** : `TravelerStatsService.myFeedbacks`/`myReports` (Angular) ajoutes, integres au chargement du dashboard traveler, deux nouvelles sections affichees ("mes avis", "mes signalements").

**À retenir** : un backend correct et teste ne suffit pas si personne ne verifie que le frontend l'appelle reellement - c'est le meme trou que celui deja identifie sur ce projet (voir #40.6). Une donnee tronquee cote frontend pour l'affichage (le `slice: 0:5` de #43.2) peut cacher une fonctionnalite backend par ailleurs complete - a verifier explicitement, pas seulement le code serveur.

## 44. 4 dernieres limites connues traitees le 27/08 - fallback travel-service, N+1 stats, messages d'erreur en anglais, verification de la copie infra/ci

**Probleme** : 4 points restaient signales comme limites connues ou non tranchees apres #43 (resilience partielle, performance des stats, coherence linguistique de l'UI, et une copie de code suspectee dans `infra/ci`). Traites le meme soir, sans exception.

### 44.1 `payment-service` retentait les pannes transitoires de `travel-service` mais n'avait aucune protection contre une panne prolongee

**Cause** : `TravelServiceClient` retentait 3 fois (200ms d'ecart) chaque appel a `travel-service`, mais si le service restait indisponible plus longtemps, CHAQUE requete de paiement refaisait ces 3 tentatives avant d'echouer - aucun mecanisme n'empechait de marteler un service deja en panne, et l'erreur remontee au client etait l'exception reseau brute (`ResourceAccessException`), pas un message clair.

**Solution** : nouveau `CircuitBreaker` (interne, sans dependance externe) - s'ouvre apres 5 echecs consecutifs et coupe court pendant 30s (les appels suivants echouent immediatement sans toucher le reseau), puis retente un seul appel d'essai (`HALF_OPEN`) : succes -> refermeture, echec -> reouverture. Un `TravelServiceUnavailableException` (503, message en francais) remplace desormais l'erreur reseau brute une fois les tentatives epuisees ou le circuit ouvert.

### 44.2 `AdminStatsService`/`ManagerStatsService` faisaient une requete SQL par voyage (N+1)

**Cause** : `managerRankings`, `travelRankings` et `myStats` calculaient le nombre d'abonnes actifs et la note moyenne de chaque voyage un par un, a l'interieur d'une boucle (`activeSubscriberCount(travel.getId())`, `feedbackRepository.findByTravel_Id(travel.getId())`) - N voyages generaient N+1 requetes au lieu d'une poignee.

**Solution** : deux requetes groupees ajoutees (`SubscriptionRepository.countActiveSubscribersGroupedByTravelIds`, `FeedbackRepository.aggregateByTravelIds`, chacune un `GROUP BY s.travel.id` avec projection), appelees une seule fois par methode avec la liste complete des ids de voyages, puis consultees via une `Map` a l'interieur de la boucle d'affichage.

### 44.3 Messages d'erreur backend systematiquement en anglais sur une UI en francais

**Cause** : chaque exception metier (403/400/404/409) portait son message directement en anglais ("Invalid credentials", "You can only cancel your own subscription", etc.), tout comme les messages generiques de secours des `ApiExceptionHandler` ("Validation failed", "Unexpected error"...) et les messages de validation par defaut de Bean Validation (`@NotBlank`, `@Email`...) - `extractErrorMessage` cote frontend affiche ce texte tel quel dans les toasts, donc CHAQUE erreur visible par un utilisateur (pas seulement une, contrairement a ce qui avait ete signale initialement) sortait en anglais sur une interface en francais.

**Solution** : traduction de tous les messages d'exception metier et des messages generiques des 4 `ApiExceptionHandler` (auth/user/travel/payment-service), plus un `ValidationMessages.properties` par service qui redefinit les messages par defaut de Bean Validation (`NotBlank`, `NotNull`, `Email`, `Size`, `Min`, `Max`, `Positive`, `PositiveOrZero`) en francais. Les messages internes jamais exposes a l'utilisateur (erreurs Vault, Stripe/PayPal, index de recherche - tous mappes en 500 generique) n'ont pas ete touches : aucun benefice utilisateur, risque de diff inutile.

### 44.4 Verification de la copie de `travel-service` sous `infra/ci/deploy-workspace/`

**Cause** : signalee comme copie dupliquee jamais nettoyee lors d'un premier passage.

**Solution** : verifiee, ce n'est pas un bug. `infra/ci/deploy-workspace/` est dans `.gitignore` et c'est l'espace de deploiement du pipeline Jenkins : l'etape `Deploy` du `Jenkinsfile` fait un `rm -rf` puis regenere entierement son contenu a partir du workspace courant a CHAQUE execution. Rien n'est fige ni versionne la-dedans, donc pas de risque de derive entre ce dossier et le vrai code source. Aucun changement necessaire.

**À retenir** : la resilience ne se limite pas au retry - un retry sans circuit breaker protege des pannes courtes mais aggrave une panne longue en continuant a solliciter un service deja mort. Sur le N+1, le reflexe "une methode privee par voyage appelee dans un stream" est le signal a chercher en premier dans du code Spring Data. Sur les messages d'erreur, une premiere estimation ("une chaine en anglais") s'est reveree etre un probleme systemique une fois qu'on a suivi le chemin complet exception -> ApiExceptionHandler -> frontend au lieu de s'arreter au premier exemple trouve. Et un signalement n'est pas automatiquement un bug : verifier avant de corriger evite de perdre du temps sur un fonctionnement de CI/CD volontaire.

## 45. `ng test` (Vitest) plantait de facon intermittente sur l'environnement WSL2/DrvFs du poste de dev - migration vers Karma/Jasmine

**Probleme** : `ng test` echouait par intermittence avec "Worker exited unexpectedly" / timeout, un fichier de test different a chaque execution, sans lien avec le code teste lui-meme.

### 45.1 Cause racine

Vitest execute chaque fichier de test dans un worker Node forke, avec un handshake IPC de demarrage borne par une constante codee en dur dans Vitest (`START_TIMEOUT = 60000ms`, verifie dans le source compile de la lib) - aucune option de `vitest.config.ts` ne permet de relever ce plafond. Sur le disque monte via DrvFs (pont WSL2 <-> NTFS) utilise pour ce projet, ce handshake est parfois trop lent et depasse le plafond : le worker est tue, le fichier de test associe echoue, un autre a la prochaine execution. Le choix de Vitest comme runner (defaut d'Angular 21, jamais decide explicitement) etait le probleme de fond, pas un reglage.

### 45.2 Migration vers Karma/Jasmine

**Solution** : `angular.json` (`runner: "karma"`), nouvelles dependances (`karma`, `karma-jasmine`, `karma-chrome-launcher`, `karma-jasmine-html-reporter`, `karma-coverage`, `jasmine-core`), `tsconfig.spec.json` (`types: ["jasmine"]`), suppression de `vitest.config.ts`. Karma execute les tests dans un vrai processus Chrome (CDP), sans mecanisme de worker/IPC forke - toute cette classe de bug disparait structurellement. Karma est une option officiellement supportee par le meme builder Angular (`runner: "karma" | "vitest"`), pas une bequille externe.

### 45.3 API de mock Vitest incompatible avec Jasmine

**Cause** : 10 fichiers `.spec.ts` utilisaient l'API de mock propre a Vitest (`vi.spyOn`, `vi.useFakeTimers`, `vi.advanceTimersByTime`...), absente de Jasmine. 4 fichiers utilisaient aussi `.toHaveLength(n)`, un matcher Jest/Vitest sans equivalent Jasmine natif.

**Solution** : `vi.spyOn(...).mockReturnValue(...)` -> `spyOn(...).and.returnValue(...)`, `vi.spyOn(x, 'p', 'get')` -> `spyOnProperty(x, 'p', 'get')`, `.toHaveLength(n)` -> `.toHaveSize(n)` (equivalent natif Jasmine).

### 45.4 Chrome headless introuvable dans l'environnement du poste de dev

**Cause** : `karma-chrome-launcher` cherche un binaire Chrome/Chromium systeme et n'en trouve pas forcement selon le poste - erreur "No binary for ChromeHeadless browser".

**Solution** : ajout de `puppeteer` en devDependency (telecharge son propre Chromium a l'installation) et d'un `karma.conf.js` qui fait `process.env.CHROME_BIN = require('puppeteer').executablePath()` avant le lancement de Karma (`angular.json` : `runnerConfig: true` pour que le builder charge ce fichier). Aucune installation systeme, aucune variable d'environnement a poser a la main : `npm install && npx ng test` suffit sur n'importe quel poste.

### 45.5 Collision d'ID de composant (`NG0912`) sur 3 composants de test factices

**Cause** : `login.spec.ts`, `register.spec.ts` et `travel-form.spec.ts` definissaient chacun un `@Component({ template: '' }) class DummyComponent {}` sans selecteur explicite - Angular leur generait a tous le meme ID interne.

**Solution** : un selecteur unique ajoute a chacun (`app-test-dummy-login`, etc.). Simple warning, aucun test en echec, corrige au passage.

### 45.6 Timer virtuel (`jasmine.clock()` puis `fakeAsync`/`tick()`) incompatible avec le debounce RxJS de `travel-browse.spec.ts`

**Cause** : les 2 tests du debounce d'autocompletion (`debounceTime(250)`) utilisaient `vi.useFakeTimers()`/`vi.advanceTimersByTime()` sous Vitest. Porte tel quel vers `jasmine.clock()`, le timer RxJS ne se declenchait jamais (`tick(250)` n'avait aucun effet observable sur la requete HTTP attendue), et l'echec du premier test empechait d'atteindre son `jasmine.clock().uninstall()`, cassant le second test en cascade ("Clock was unable to install... already installed?"). Tentative suivante avec `fakeAsync`/`tick()` d'Angular : echec different, `zone.js/testing` introuvable - ce projet est une app Angular 21 zoneless, sans zone.js du tout, et `fakeAsync` en depend structurellement.

**Solution** : abandon du temps virtuel pour ces 2 tests au profit d'un vrai delai (`await new Promise((resolve) => setTimeout(resolve, 300))`), superieur aux 250ms du debounce. Plus robuste qu'un mock de timer dont la compatibilite avec le scheduler interne de RxJS (`intervalProvider`) n'est pas garantie selon le runner/environnement, au prix de 300ms reels par test - negligeable sur une suite de cette taille.

**À retenir** : un choix d'outillage jamais explicitement decide (Vitest par defaut d'Angular 21) peut devenir un probleme de fond sur un environnement particulier (ici WSL2/DrvFs) - remonter au choix de methode plutot que d'accumuler des reglages de config est parfois la seule vraie solution. Porter des tests d'un runner a un autre ne se limite pas a changer le builder : verifier systematiquement l'API de mock utilisee (`vi.*` vs Jasmine) et les mecanismes qui en dependent implicitement (zone.js pour `fakeAsync`) avant de declarer la migration terminee. Et face a un mock de temps qui ne se comporte pas comme attendu, un vrai petit delai reste parfois la solution la plus fiable - moins elegant, mais qui ne depend d'aucune hypothese sur la compatibilite entre librairies.
## 46. Deuxieme re-verification adversariale du 27/08 (4 agents en parallele, chaque point de l'audit relu contre le code reel) - 2 erreurs de documentation trouvees, une couverture de test manquante comblee

**Probleme** : apres la fermeture des points #43/#44/#45, tous les points de `lets-travel_audit.md` etaient marques SOLIDE dans `audit_reponses_detaillees.md`. Plutot que de faire confiance a ce document, relecture independante de CHAQUE affirmation contre le code source reel (pas contre sa propre description), un lot d'audit a la fois (comprehension, fonctionnel/securite, frontend/RGPD, couverture de test des correctifs recents).

### 46.1 `docker-compose.yml` fait deja dependre le demarrage de `travel-service` de la sante de Neo4j/Elasticsearch

**Cause** : `audit_reponses_detaillees.md` affirmait "une panne d'ES/Neo4j n'empeche pas travel-service de demarrer" - faux au demarrage : `travel-service` a bien `depends_on: { neo4j: condition: service_healthy, elasticsearch: condition: service_healthy }`, un choix de fail-fast delibere (ne pas demarrer avec une dependance critique cassee), pas un oubli, mais l'affirmation etait trop large.

**Solution** : documentation corrigee - l'independance operationnelle (scaling, cycle de vie hors demarrage) reste vraie, mais le demarrage est bien couple par choix. Aucun changement de code : le comportement actuel (fail-fast) est le bon choix, seule la description etait fausse.

### 46.2 "mTLS interne entre microservices" etait un abus de langage - c'est du TLS a sens unique avec confiance partagee, pas une authentification mutuelle par certificat

**Cause** : verification du code de chaque service (`application-docker.properties`) : aucun ne configure `server.ssl.client-auth=need`/`want`. Chaque service presente son certificat serveur (bundle `internal-services`) et ses appelants verifient ce certificat via un truststore partage - c'est du TLS chiffre et a sens unique authentifie cote serveur, pas du mTLS (qui exigerait aussi une authentification du client par certificat).

**Solution** : vocabulaire corrige dans `audit_reponses_detaillees.md`. L'exigence reelle de l'audit ("donnees transmises de maniere securisee via SSL/TLS") est deja pleinement satisfaite par le TLS a sens unique existant - **aucune correction de code necessaire pour repondre a l'audit**. Piste identifiee si un vrai mTLS est voulu au-dela de l'exigence : `payment-service`/`user-service`/`api-gateway` presentent deja ce meme certificat cote client sortant (`spring.http.client.ssl.bundle=internal-services`), donc activer `client-auth=need` sur `travel-service` (seulement appele par ces deux-la) serait sans risque - mais l'activer sur `api-gateway` casserait TOUT le trafic entrant : nginx verifie deja le certificat serveur d'api-gateway (`proxy_ssl_verify on`) mais ne presente lui-meme AUCUN certificat client (pas de `proxy_ssl_certificate`). Un vrai mTLS complet demanderait de monter le certificat interne dans le conteneur nginx en plus. Volontairement NON applique : ce changement touche le TLS de bout en bout entre nginx et 5 services, ne peut pas etre verifie sans un vrai redemarrage complet de la stack (`docker compose up`), et n'est pas requis par l'audit - decision a prendre par toi si tu veux aller au-dela de l'exigence, pas quelque chose a activer a l'aveugle depuis ce siege.

### 46.3 Les messages d'erreur traduits en francais (#44.3) n'etaient testes que dans `auth-service` - une regression sur les 3 autres services serait passee inapercue

**Cause** : seul `auth-service` avait un `ApiExceptionHandlerTest.java` qui verifie le CONTENU du message ("Nom d'utilisateur deja utilise", etc.). `payment-service`, `travel-service` et `user-service` n'avaient aucun test verifiant le texte des messages generiques (validation, corps malforme, parametre invalide, erreur inattendue) - seulement des tests de statut HTTP/type d'exception, qui passeraient meme si le texte anglais revenait par erreur.

**Solution** : `ApiExceptionHandlerTest.java` ajoute aux 3 services manquants, sur le meme modele qu'`auth-service` (test unitaire direct du handler, sans contexte Spring), verifiant le texte francais exact de chaque message generique et, pour `payment-service`, des 2 handlers specifiques a ce service (en-tete manquant, echec d'appel amont).

**À retenir** : une documentation d'audit qui n'est plus relue contre le code reel derive silencieusement - deux affirmations ("independance au demarrage", "mTLS") etaient devenues fausses ou approximatives sans qu'aucun bug de code n'existe derriere. Verifier une affirmation de securite/architecture veut dire lire la config reelle (`client-auth`, `depends_on`), pas relire la documentation qui la decrit. Et un message traduit sans assertion de contenu dans les tests n'est protege que par hasard - traduire un message et le tester sont deux etapes distinctes, la seconde ne decoule pas automatiquement de la premiere.

## 47. Troisieme ronde de verification (mot a mot contre l'enonce colle par toi) - 3 ecarts fonctionnels/UI reels trouves et corriges

**Probleme** : apres les rondes #45/#46, une derniere verification ligne par ligne contre le texte exact de `lets-travel_project.md` et `lets-travel_audit.md` (colle integralement, pas resume) a trouve 3 ecarts fonctionnels reels, tous signales avant correction et valides comme "facilement fixables".

### 47.1 Un compte ADMIN ne pouvait pas s'abonner a un voyage ni laisser un avis

**Cause** : l'enonce demande qu'un ADMIN puisse "perform all actions available to Travel Managers and Travelers". Cote roles HTTP c'etait deja vrai, mais `SubscriptionService`/`FeedbackService` rejettent tout appelant dont le `userId` est null - or `AdminSeeder` creait le compte ADMIN par defaut sans fiche `User` liee (`userId=null`), rendant l'abonnement et l'avis impossibles en pratique pour cet ADMIN precis.

**Solution** : un UUID fixe (`00000000-0000-0000-0000-000000000001`) est desormais partage entre `AdminSeeder` (auth-service) et un nouveau `AdminProfileSeeder` (user-service) qui cree la fiche `User` correspondante au demarrage - sans appel inter-service supplementaire, chaque service seme sa propre table avec le meme id fixe. Gere le cas multi-replicas via une verification `existsById` puis un rattrapage sur `DataIntegrityViolationException`.

### 47.2 Le tableau des signalements a moderer (dashboard admin) n'affichait pas qui avait depose le signalement

**Cause** : `reporterId` etait deja recupere par le frontend mais jamais resolu en nom ni affiche - seule la cible (`reportedId`) et le motif etaient visibles.

**Solution** : `resolveReportedNames` renommee `resolveUserNames` et etendue pour resoudre `reportedId` ET `reporterId` en un seul appel groupe (meme `forkJoin`, pas de requete HTTP supplementaire), nouvelle colonne "Signale par" ajoutee au tableau.

### 47.3 La page publique d'un manager n'affichait qu'une seule note moyenne, pas les notes par voyage

**Cause** : l'enonce demande explicitement que le Traveler puisse voir les "past travel ratings" (au pluriel) sur cette page. `ManagerPublicStatsResponse` ne renvoyait qu'une moyenne globale agregee sur tous les voyages du manager, aucun detail voyage par voyage.

**Solution** : `ManagerPublicStatsResponse` porte desormais un champ `travelRatings` (liste de `travelId`/`title`/`averageRating`/`feedbackCount`, un par voyage du manager), calcule via la meme requete groupee que le tableau de bord prive du manager (`FeedbackRepository.aggregateByTravelIds`, pas de N+1). Le frontend (`manager-public.html`) affiche ce detail dans un tableau sous les compteurs globaux, uniquement si le manager a au moins un voyage.

**À retenir** : comparer le code a un document interne qui resume l'enonce (aussi fidele soit-il) n'est pas equivalent a le comparer au texte exact de l'enonce - une reformulation plus generale peut faire disparaitre une exigence precise (ex : "past travel ratings" au pluriel devient silencieusement "note du manager" au singulier dans un resume). Les 3 ecarts trouves ici etaient tous dans du code deja fonctionnel a 90% (une valeur null non geree en aval, un champ recupere mais jamais affiche, un agregat global la ou un detail etait demande) - le genre d'ecart qui survit facilement a une revue de haut niveau et ne se voit qu'en confrontant chaque phrase du cahier des charges au code reel plutot qu'a sa propre synthese.


## 48. `vault` recree par `deploy.yml` juste apres avoir ete descelle par `vault-unseal.yml` - toute la stack reste unhealthy en cascade

**Probleme** : `ansible-playbook site.yml` (ou le meme flux via Jenkins) plante systematiquement au meme endroit - `vault-init` ne demarre jamais ("dependency failed to start: container ... vault-1 is unhealthy"), puis `fetch-vault-secrets.yml` echoue avec "Vault is sealed" apres 10 tentatives, alors meme que `vault-unseal.yml` (jouee juste avant dans le meme run) venait de confirmer Vault descelle avec succes quelques secondes plus tot.

**Cause** : `deploy.yml` force-supprime le conteneur `vault` (task ajoutee par le fix #37, pour contourner un bug de bind-mount perime WSL2/Docker Desktop) juste avant de lancer `docker compose up` sur toute la stack. Vault demarre TOUJOURS scelle sur un conteneur neuf (son etat de scellement vit en memoire, jamais persiste avec le volume de donnees) - la recreation efface donc instantanement le travail de `vault-unseal.yml`. `vault-init` depend de `vault` a l'etat `healthy` (qui exige d'etre descelle) : jamais atteint, `vault-init` ne demarre jamais, aucun role AppRole n'est jamais seme, et tout le reste de la chaine echoue en cascade a partir de la.

**Solution** : ajout dans `deploy.yml`, juste apres le force-remove de `vault` et juste avant le `docker compose up` complet, d'un bloc qui redemarre `vault` seul, attend que son CLI reponde, et le redescelle avec la cle deja stockee sur disque (meme sequence que `vault-unseal.yml`, reutilisee ici a l'identique) - avant que la commande `docker compose up` n'evalue la dependance `vault-init -> vault healthy`.

**A retenir** : le fix #37 (ajouter `vault` a la liste de force-remove) etait correct pour son propre probleme (bind-mount perime) mais n'avait pas anticipe qu'il defaisait le travail d'un AUTRE playbook execute juste avant dans le meme run (`vault-unseal.yml`). Des que deux playbooks touchent au meme conteneur avec des effets qui ne sont pas idempotents l'un par rapport a l'autre (ici : descellement vs recreation), il faut retracer la sequence complete de `site.yml` (pas juste le playbook qu'on modifie) pour verifier qu'aucune etape ulterieure n'annule silencieusement le travail d'une etape anterieure.

## 49. `user-service` (2 replicas) plante au demarrage sur `ObjectOptimisticLockingFailureException` en semant le profil admin - pas rattrape par le catch existant

**Probleme** : juste apres le fix #48 (Vault reste descelle), 2e passe de `deploy.yml` echoue quand meme : `user-service-1` ET `user-service-2` finissent en **Error** (crash, pas juste "unhealthy"), avec dans `docker logs` :
```
org.springframework.orm.ObjectOptimisticLockingFailureException: Row was already updated or deleted
by another transaction for entity [com.travel_plan.user_service.domain.User with id '00000000-...-1']
	at com.travel_plan.user_service.bootstrap.AdminProfileSeeder.run(AdminProfileSeeder.java:40)
```
Docker relance le conteneur (restart policy), qui reussit generalement au 2e essai (l'admin existe deja a ce moment-la), mais `docker compose up` remonte un code de sortie non-nul le temps que ca se stabilise, ce qui fait echouer la tache "Fail loudly unless this pass is expected to leave services unhealthy".

**Cause** : `AdminProfileSeeder` (comme `auth-service.AdminSeeder`, meme intention) doit gerer la course entre les 2 replicas qui demarrent en parallele et tentent tous les deux de creer le profil admin par defaut. Le code catchait deja `DataIntegrityViolationException` en pensant a une violation de contrainte unique classique (c'est exactement ce qui se passe cote `auth-service`, cf. #34, ou l'ID est genere). Mais ici `User.id` est fixe explicitement (`.id(ADMIN_USER_ID)`) pour rester identique a `auth-service`, alors que le champ est annote `@UuidGenerator` (generateur cote Hibernate). Un ID deja renseigne sur une entite avec generateur pousse Spring Data JPA a appeler `entityManager.merge()` (chemin "mise a jour d'une entite detachee") au lieu de `persist()` (chemin "nouvelle entite"). Sous course entre 2 replicas, `merge()` leve `ObjectOptimisticLockingFailureException`/`StaleObjectStateException` - pas `DataIntegrityViolationException` - donc le catch existant ne l'attrapait jamais.

**Solution** : `AdminProfileSeeder.java` catche maintenant aussi `ObjectOptimisticLockingFailureException`, au meme titre que `DataIntegrityViolationException` (les deux signifient "un autre replica l'a deja cree, rien a faire"). Nouveau test `logsAndContinuesWhenAnotherReplicaWonTheMergeRaceConcurrently` ajoute a `AdminProfileSeederTest` a cote du test existant sur `DataIntegrityViolationException`, pour couvrir le vrai type d'exception observe en pratique.

**A retenir** : deux seeders qui semblent suivre le meme patron ("check puis save, catch la violation de contrainte") peuvent echouer differemment des que l'un des deux force un ID explicite sur une entite a generateur - `save()` de Spring Data JPA ne prend pas le meme chemin (`persist()` vs `merge()`) selon que l'ID est deja renseigne ou non, et chaque chemin leve un type d'exception different sous la meme course. Un test qui mocke `save()` pour lever exactement l'exception qu'on imagine (ici `DataIntegrityViolationException`, copie du seeder voisin) donne une fausse confiance s'il ne reflete pas le vrai comportement d'Hibernate pour CE seeder precis - verifier contre un vrai `docker logs`, pas seulement contre le test deja en place.

## 50. `GET /api/reports` inatteignable via la gateway - confirme par le premier run k6

**Probleme** : le scenario `admin_dashboard` du test de charge k6 (`k6/lets-travel-load-test.js`) echoue a 0% sur le check `admin: reports reachable` (35 echecs sur 35, aucune reussite), alors que les autres routes admin du meme scenario (classements, revenu mensuel) passent a 100%.

**Cause** : `ReportController.listAll()` (travel-service) expose `GET /api/reports`, hors du prefixe `/api/travels/**`. Le bean `travelServiceRoutes` de `RouteConfig` (api-gateway) ne routait QUE `/api/travels/**` vers travel-service - `/api/reports` ne correspondait a aucune route declaree, ni ici ni ailleurs, et tombait donc en 404 avant meme d'atteindre travel-service. Cote securite, `SecurityConfig` (travel-service) protegeait deja correctement `GET /api/reports` (`hasRole(ADMIN_ROLE)`) - le probleme etait uniquement le routage gateway, jamais rajoute quand cet endpoint a ete introduit.

**Solution** : ajout d'une route separee `travel-service-reports` (`/api/reports/**` -> travel-service) dans `travelServiceRoutes`, au meme titre que les 2 routes deja separees de `paymentServiceRoutes` (`/api/payments/**` et `/api/payment-methods/**`) - meme pattern, un controller avec un chemin hors du prefixe principal de son service a besoin de sa propre entree de route.

**A retenir** : ce gap avait deja ete repere en lisant le code (`RouteConfig.java` compare a `ReportController.java`) avant meme de lancer les tests, et signale a l'utilisateur - le premier run k6 vient seulement de le confirmer avec un vrai HTTP 404 en conditions reelles plutot qu'une lecture de code. Un controller qui expose un chemin ne partageant pas le prefixe REST habituel de son service (ici `/api/reports` a cote de `/api/travels/**`) est le symptome a chercher systematiquement dans `RouteConfig.java` a chaque nouvel endpoint de ce genre.

## 51. `scripts/start-app.sh` (fix du jour pour #48) ne descellait pas Vault a cause d'un parsing JSON fragile en `sed`

**Probleme** : meme apres avoir ajoute a `scripts/start-app.sh` la meme logique de re-descellement que dans `deploy.yml` (#48), `./scripts/start-app.sh` echouait encore sur `dependency failed to start: container lets-travel-app-vault-1 is unhealthy` - alors que le script affichait bien "Attente de Vault..." (donc s'executait), sans jamais afficher "Descellement de Vault...".

**Cause** : contrairement a la version Ansible (qui parse le JSON de `vault status -format=json` avec `from_json`, un vrai parseur), la version shell utilisait un `sed` fait main (`sed -n 's/.*"sealed":\([a-z]*\).*/\1/p'`) pour extraire le champ `sealed`. Ce pattern suppose `"sealed":true` colle sans espace - si le JSON reel contient un espace apres les deux-points (ou tout autre leger ecart de format), le `sed` ne matche rien, la variable `sealed` reste vide, et `[ "$sealed" = "true" ]` est silencieusement faux : le script saute le descellement sans jamais signaler d'erreur.

**Solution** : suppression totale du parsing JSON dans `start-app.sh`. `vault status` a un code de sortie documente et stable (0 = descelle, 1 = injoignable/erreur, 2 = scelle) - le script se base directement dessus (`rc`) au lieu de parser du texte, plus robuste et plus simple.

**A retenir** : parser la sortie texte/JSON d'une commande CLI a la main (`sed`/`grep`/`awk`) est fragile des qu'un format peut varier legerement - preferer le code de sortie quand l'outil en documente un qui porte deja l'information cherchee (ici, Vault documente explicitement 0/1/2), ou un vrai parseur JSON (`jq`, `from_json` d'Ansible) sinon. Un script qui semble tourner sans erreur (`set -e` ne se declenche pas) peut quand meme silencieusement sauter une etape critique si un test de condition (`[ "$x" = "valeur" ]`) est simplement faux a cause d'une variable mal extraite - toujours verifier qu'une etape censee s'executer a bien laisse une trace (ici : l'absence du message "Descellement de Vault..." dans la sortie collee par l'utilisateur a ete l'indice determinant).

## 52. `docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7688` echoue - mauvais port, bug preexistant dans la doc

**Probleme** : `docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7688 -u neo4j -p ...` (commande documentee telle quelle dans `10-audit-demo-guide.md` et `11-audit-cheatsheet.md`) echoue systematiquement : "Unable to connect to localhost:7688, ensure the database is running...", meme quand Neo4j est confirme `healthy` par `docker compose ps`.

**Cause** : confusion entre le port publie sur l'hote et le port interne au conteneur. `docker-compose.yml` mappe `127.0.0.1:7688:7687` (host:conteneur) - le `7688` n'existe QUE du point de vue de l'hote (Windows/WSL2), pour un client externe (Neo4j Browser/Desktop). Mais `docker compose exec neo4j ...` execute la commande DANS le conteneur neo4j lui-meme : a l'interieur, `localhost` c'est le conteneur, qui n'ecoute que sur son port interne `7687` (celui utilise en interne par `travel-service` via `NEO4J_URI=bolt+ssc://neo4j:7687`) - `7688` n'y existe pas.

**Solution** : les 4 occurrences de `cypher-shell -a bolt+ssc://localhost:7688` dans `docs/10-audit-demo-guide.md` et `docs/11-audit-cheatsheet.md` (toutes lancees via `docker compose exec`) corrigees en `bolt+ssc://localhost:7687`. Aucun changement pour la connexion depuis un client externe (Neo4j Browser/Desktop sur la machine hote) - la, `localhost:7688` reste correct, c'est un contexte different (hors conteneur).

**A retenir** : pour tout service dont le port publie differe du port interne (ici +1, decale expres pour coexister avec `travel-plan`), bien distinguer les deux avant d'ecrire une commande - `docker compose exec <service> ...` s'execute TOUJOURS du point de vue interne au conteneur (port interne), alors qu'un outil lance depuis l'hote (navigateur, client desktop, `curl` direct sans passer par `exec`) utilise TOUJOURS le port publie. Cette commande etait dans la doc depuis un moment sans etre remarquee errronee - vraisemblablement jamais executee telle quelle avant que Daro ne la teste en conditions reelles ce soir.

## 53. Suite Playwright : `manager-journey.spec.ts` reste sur `/login` - trop d'appels `/api/auth/login` cumules malgre l'execution serie

**Probleme** : au premier vrai run complet de la suite e2e, `manager-journey.spec.ts` echoue des son `beforeAll` : apres avoir rempli et soumis le formulaire de login, la page reste sur `/login` au lieu de `/dashboard`. `workers: 1` (execution serie) etait deja en place pour respecter la limite nginx sur `/api/auth/login` (5r/m, burst=3), mais elle a quand meme ete atteinte.

**Cause** : chaque fichier de specs qui avait besoin d'une session (admin, manager) refaisait son PROPRE login via le formulaire UI dans son `beforeAll`, en plus des logins deja faits par `global-setup.ts` (API, une fois pour l'admin ET le manager, pour preparer les donnees). Resultat : le meme compte manager se connectait 2 fois (une fois dans `global-setup.ts`, une 2e fois dans `manager-journey.spec.ts`), et sur une suite qui tourne entierement en moins de 40 secondes, ces appels s'accumulent bien plus vite qu'"un par minute" - le burst de nginx finit par etre depasse.

**Solution** : `global-setup.ts` sauvegarde desormais aussi `adminToken`/`managerToken` (deja obtenus une fois, jamais exploites au-dela de la creation des donnees) dans `.fixtures/run.json`. `admin-journey.spec.ts` et `manager-journey.spec.ts` injectent directement ce token dans le `localStorage` (`travel-plan.admin.token`) au lieu de resoumettre le formulaire de login - le guard Angular (`authGuard`) rappelle `/me` (non rate-limite) pour restaurer la session, exactement comme apres un rechargement de page reel. Seul `auth.spec.ts` continue de passer par le vrai formulaire, ce qui est son role (tester le login lui-meme).

**A retenir** : "executer les tests en serie" ne suffit pas a lui seul a respecter un rate limit si le NOMBRE d'appels reste trop eleve - il faut aussi eliminer les appels redondants (ici, un compte qui se connectait 2 fois pour 2 raisons differentes). Quand un token/une session a deja ete obtenue une fois quelque part dans la suite, la reutiliser (via `localStorage`, pas via une resoumission du formulaire) est strictement equivalent du point de vue de l'application testee, sans consommer une nouvelle fois une ressource limitee en debit.

## 54. Suite Playwright : `admin-journey.spec.ts` - "strict mode violation" sur la liste des utilisateurs (3 elements matches)

**Probleme** : `la liste des utilisateurs est accessible et montre le manager de fixture` echoue avec "strict mode violation... resolved to 3 elements" - le locator `getByText(fixture.managerUsername).or(getByText('E2E Manager'))` matche a la fois l'email du manager de CE run et DEUX cellules "E2E Manager" (nom affiche, identique a chaque run).

**Cause** : la suite e2e ne nettoie jamais ses donnees entre deux executions (voulu : chaque run cree ses propres comptes/voyages sans toucher a l'existant). Le manager de fixture a toujours pour nom affiche "E2E Manager" (`firstName`/`lastName` fixes dans `apiCreateManager`), donc des qu'on a lance la suite plus d'une fois sur la meme base, plusieurs managers differents (usernames distincts, horodates) partagent le meme nom affiche - le `.or(getByText('E2E Manager'))`, pense a l'origine comme filet de securite, devient une source d'ambiguite des que les runs s'accumulent.

**Solution** : le locator ne s'appuie plus que sur `fixture.managerUsername` (unique et horodate par construction), avec `.first()` pour tolerer que le texte apparaisse dans plusieurs cellules de la meme ligne (nom + email) sans jamais viser un autre manager.

**A retenir** : dans une suite qui ne nettoie jamais ses donnees de test, un `.or(texte generique)` ajoute "par securite" autour d'un identifiant deja unique (ici l'username horodate) est une fausse bonne idee - il ne rend pas le test plus robuste, il cree une ambiguite qui n'apparait qu'apres plusieurs runs, donc pas au moment ou on ecrit le test.

## 55. Changer `STRIPE_SECRET_KEY` dans `.env` ne suffit pas - `payment-service` lit Vault, pas l'environnement directement

**Probleme** : apres avoir remplace le placeholder `STRIPE_SECRET_KEY=sk_test_changeme_dev_only` par une vraie cle Stripe dans `.env`, puis redemarre `payment-service` (`docker compose up -d payment-service`), Stripe continue de rejeter la requete avec exactement la meme erreur qu'avant : "Invalid API Key provided: sk_test_*************only" - la cle utilisee reste visiblement le placeholder, malgre le `.env` a jour.

**Cause** : `payment-service` ne lit pas `STRIPE_SECRET_KEY` directement depuis son environnement - `infra/vault/bootstrap.sh` (execute par le conteneur `vault-init`) ecrit cette valeur UNE FOIS dans Vault (`vault kv put secret/payment-service/stripe secret_key="${STRIPE_SECRET_KEY:-...}"`), et c'est CETTE copie dans Vault que `payment-service` consulte au demarrage. `vault-init` ne se relance pas quand on cible un seul service avec `docker compose up -d payment-service` - il n'y a donc plus aucune etape qui repropage un `.env` modifie vers Vault une fois que Vault a deja ete initialise avec l'ancienne valeur (ici, initialise plus tot dans la meme session, quand `.env` avait encore le placeholder).

**Solution** : ecrire directement la nouvelle valeur dans Vault, puis redemarrer `payment-service` pour qu'il la relise a son prochain demarrage :
```
docker compose exec -T vault vault kv put secret/payment-service/stripe secret_key="<vraie cle>"
docker compose restart payment-service
```

**A retenir** : tout secret seede par `vault-init` (Stripe, PayPal, JWT partage - voir `bootstrap.sh`) suit ce meme principe : une fois Vault initialise, changer `.env` seul ne met plus rien a jour automatiquement - il faut soit ecrire directement dans Vault (`vault kv put`, ci-dessus), soit forcer un reseed complet (ce qui implique de re-desceller/reinitialiser Vault, une operation beaucoup plus lourde). Avant de soupconner un bug applicatif sur un secret qui semble "ne pas se mettre a jour", verifier d'abord s'il transite par Vault plutot que par une variable d'environnement lue en direct.

## 56. Suite Playwright : `manager-journey.spec.ts` - creation de voyage bloquee, formulaire invalide en silence

**Probleme** : le test `cree un nouveau voyage via le formulaire` expire (`page.waitForResponse` timeout 15s) en attendant le `POST /api/travels` - le clic sur "$ save" ne declenche jamais la requete. La capture de page au moment de l'echec montre 2 destinations : la premiere bien remplie (Lyon/France), la seconde entierement vide.

**Cause** : `travel-form.ts` (`ngOnInit`) appelle deja `addDestination()` une fois a l'ouverture du formulaire "nouveau voyage" - une destination vide existe donc par defaut. Le test cliquait EN PLUS sur "+ add destination" avant de remplir `.subcard.first()`, ce qui ajoutait une 2e destination (vide, champs obligatoires) au lieu de remplir celle deja presente. Angular bloque la soumission d'un formulaire reactif invalide sans message d'erreur explicite ici, donc le clic sur "$ save" ne fait rien - d'ou le timeout, qui ne dit pas directement "formulaire invalide".

**Solution** : suppression du clic sur "+ add destination" dans le test - `.subcard.first()` cible directement la destination deja presente par defaut.

**A retenir** : un `page.waitForResponse` qui expire sans qu'aucune requete ne parte du tout (par opposition a une requete qui echoue avec un code d'erreur) est souvent un signe de validation cote client qui bloque la soumission en silence - verifier l'etat reel du formulaire (ici via la capture de page Playwright, `error-context.md`) plutot que de supposer un probleme reseau ou backend.

## 57. Paiement Stripe : "No such PaymentMethod: 'tok_visa'" - mauvais type d'identifiant de test dans les donnees de test

**Probleme** : une fois la vraie cle Stripe en place dans Vault (#55), le paiement echoue encore, mais avec une erreur differente et 400 (pas 401) : `"No such PaymentMethod: 'tok_visa'"`, `code: resource_missing`.

**Cause** : `StripePaymentProvider.charge()` (payment-service) envoie `payment_method: request.providerToken()` directement a l'API `/v1/payment_intents` de Stripe. Cette API attend un identifiant de PaymentMethod (`pm_...`) - `tok_visa` est un identifiant de Token (`tok_...`), une ressource Stripe differente, propre a l'ancienne API Charges/Sources, pas a l'API PaymentIntents utilisee ici. Le code applicatif est correct ; c'est la valeur de test que j'avais choisie (k6, e2e) qui utilisait le mauvais type d'identifiant.

**Solution** : remplacement de `tok_visa` par `pm_card_visa` (identifiant de test officiel Stripe, documente pour un usage direct sans passer par Stripe.js) dans `k6/lets-travel-load-test.js`, `k6/README.md` et `e2e/tests/traveler-journey.spec.ts`.

**A retenir** : Stripe a plusieurs familles d'identifiants de test qui se ressemblent (`tok_...` pour les Tokens, `pm_...` pour les PaymentMethods, `src_...` pour les Sources) et ne sont PAS interchangeables selon l'API appelee - toujours verifier, cote code applicatif, quel parametre est envoye a quelle API Stripe avant de choisir la valeur de test correspondante, plutot que de reutiliser un identifiant de test "classique" trouve dans un exemple externe sans le confronter au code reel.

## 58. L'admin peut creer un utilisateur mais jamais lui donner de compte de connexion

**Probleme** : le formulaire "creer un utilisateur" de l'admin ne creait qu'une fiche profil (user-service) - aucun chemin de l'UI ne permettait de creer, en plus, un compte de connexion (auth-service) utilisable pour un TRAVELER ou un TRAVEL_MANAGER ainsi cree.

**Cause** : `UserService.create()` se limitait a `userRepository.save(user)`, sans jamais appeler auth-service. Seul `POST /api/auth/accounts` (deja fonctionnel, utilise par les tests e2e) pouvait creer un compte, mais rien dans ce flux ne l'appelait.

**Solution** : ajout de `username`/`password` optionnels a `UserRequest` (avec `@AssertTrue` : les deux fournis ou aucun), d'un `AuthServiceClient.createAccount()`, et d'un appel conditionnel dans `UserService.create()` - jamais declenche pour ADMIN, qui n'a pas de fiche User liee. Cote UI, une case a cocher "creer un compte de connexion" revele les champs correspondants, visible uniquement a la creation (pas en edition) et hors role ADMIN.

**A retenir** : ce n'etait pas un point demande par l'audit (celui-ci porte sur la creation de profils, pas explicitement sur le provisioning de comptes), mais un gap reel decouvert en testant manuellement l'app - un manager cree depuis l'admin ne pouvait jamais se connecter tant que ce chemin n'existait pas. Verifie par les tests unitaires (backend et frontend, tous verts) et par une creation + connexion reelles sur la stack en marche.
