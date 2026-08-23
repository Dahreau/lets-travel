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
