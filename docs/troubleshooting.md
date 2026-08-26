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
3. Reste un défaut de conception à corriger un jour (pas urgent) : monter aussi `infra/ci/persistent-state` (ou tout `infra/ci`) dans le conteneur Jenkins, pour que cette sauvegarde soit une vraie persistance sur disque et pas seulement une survie tant que le conteneur `lets-travel-jenkins` n'est pas recréé.

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

