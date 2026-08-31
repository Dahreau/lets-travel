# Let's Travel — vue d'ensemble technique

Document de reference synthetique sur l'etat du projet, par theme. Version condensee de la documentation technique complete (`troubleshooting.md`, commentaires de code) - a garder sous la main comme pense-bete plutot que de tout reciter de memoire.

## Architecture et donnees

Cinq services Spring Boot independants (auth, users, travels, payments, gateway) derriere un reverse-proxy nginx en TLS, avec mTLS entre les services eux-memes. PostgreSQL pour le stockage transactionnel, Elasticsearch pour la recherche/autocompletion (index mis a jour en synchrone a chaque creation/modification de voyage), Neo4j pour les recommandations personnalisees (graphe voyageurs/voyages mis a jour en synchrone a chaque inscription/feedback). Les trois bases restent coherentes en usage normal, sans mecanisme de rattrapage automatique en cas d'echec partiel (compromis assume pour la taille du projet, pas une architecture a coherence distribuee garantie).

## Securite

Mots de passe en BCrypt, secrets (cles de paiement, identifiants) geres via Vault plutot qu'en dur dans le code, TLS de bout en bout (externe et interne). Controle d'acces par role (Admin/Travel Manager/Traveler) verifie a deux niveaux : le role HTTP d'abord, puis une verification de relation/propriete cote service pour toute ressource individuelle (un manager ne voit que SES voyages et SES abonnes, un traveler ne modifie que SES propres donnees). Un point d'acces trop large a ete trouve puis corrige : un manager pouvait auparavant consulter le profil de n'importe quel utilisateur au lieu de seulement ses abonnes reels (voir `troubleshooting.md` #38). Ajouts recents en defense en profondeur : limite de frequence sur la connexion (anti brute-force), headers de securite HTTP (anti-clickjacking, CSP) au niveau nginx. Protection des donnees personnelles : consentement obligatoire a l'inscription, page self-service "mon compte" pour consulter/exporter son profil et supprimer definitivement son compte (profil + identifiants de connexion) - voir `troubleshooting.md` #41 pour la limite assumee sur les donnees cross-service (abonnements/feedbacks/paiements) qui ne sont pas purgees individuellement.

## Performance et resilience

Aucun circuit-breaker ni mode degrade automatique en cas de panne d'un service - une panne totale remonte desormais en erreur JSON exploitable plutot qu'en page HTML brute, mais reste une erreur franche, pas un fallback fonctionnel. Timeouts explicites sur tous les appels HTTP sortants (internes et vers les fournisseurs de paiement externes). Index de base de donnees ajoutes sur les colonnes de filtrage frequent. Aucun test de charge formalise a ce jour (k6/JMeter) - le script de verification manuelle inclut une mesure de latence simple, insuffisante pour valider un objectif de charge/performance chiffre.

## Experience utilisateur

Interface adaptee au role connecte (navigation, dashboards) et reactive sur les tailles d'ecran courantes (desktop/tablette/mobile), sans etre testee de facon exhaustive. Pas de support multilingue, pas d'implementation en Progressive Web App - deux axes clairement identifies comme non traites plutot que partiellement faits.

## Qualite de code

Separation controller/service/repository respectee dans la grande majorite du projet, a une exception pres (les controleurs d'authentification acceder directement au repository sans couche service). Requetes de base de donnees exclusivement via les methodes Spring Data JPA - aucune requete construite dynamiquement, donc pas de surface d'injection SQL. Angular echappe le contenu par defaut, aucun usage risque trouve qui contournerait cette protection.

## Ce qui reste a faire

Support multilingue et mode Progressive Web App - hors scope par decision explicite. Un vrai test de charge chiffre (k6/JMeter) reste un chantier non traite, distinct des deux precedents (pas un choix de perimetre, une limite de temps).
