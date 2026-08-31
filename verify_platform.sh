#!/bin/bash
# ==============================================================================
# verify_platform.sh — script de verification manuelle "Let's Travel"
#
# Remplace test_idor.sh (conserve tel quel, ce script fait plus large : IDOR +
# controle de role + rate-limit login + headers de securite + sondes SQLi/XSS +
# annulation d'abonnement (delai 3 jours) + acces traveler a son propre
# feedback/reports + conformite RGPD (consentement/acces/effacement, #41) +
# une mesure de latence indicative).
#
# A LANCER UNIQUEMENT APRES `docker compose up -d --build` complet ET apres
# avoir verifie que api-gateway a fini de demarrer (`docker compose logs
# api-gateway --tail 5`, chercher "Started ApiGatewayApplication" x2 replicas) -
# sinon les premieres requetes echoueront en 502 (pas un vrai echec de test,
# juste un demarrage encore en cours - voir troubleshooting.md #39).
#
# Chaque test suit le meme format : QUOI on teste, COMMENT (l'appel HTTP
# reellement effectue), POURQUOI on s'attend a ce resultat precis (quel
# controle du code le garantit), et le resultat OBTENU compare a l'ATTENDU.
# ==============================================================================

set +H
BASE=https://localhost:8443
SUFFIX=$(date +%s)
PASS=0
FAIL=0

section() { echo; echo "=============================================================="; echo "$1"; echo "=============================================================="; }

# check_status <description> <expected_code> <actual_code>
check_status() {
    local desc="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "  [OK]   $desc -> $actual (attendu $expected)"
        PASS=$((PASS+1))
    else
        echo "  [FAIL] $desc -> $actual (attendu $expected)"
        FAIL=$((FAIL+1))
    fi
}

extract_field() {
    # extrait le PREMIER champ JSON "champ":"valeur" d'une reponse (grep+cut plutot que jq,
    # pas garanti installe sur tous les postes)
    echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -n1 | cut -d'"' -f4
}

# ------------------------------------------------------------------------------
section "SETUP — creation des comptes de test (traveler A, traveler B, manager, admin)"
# QUOI/COMMENT : reprend exactement le parcours de test_idor.sh (register user ->
# register auth account -> login), avec un SUFFIX horodate pour rejouer le
# script sans conflit d'email/username (bug corrige le 26/08 sur ce script).
# "acceptedPrivacyPolicy":true obligatoire depuis le 26/08 (troubleshooting.md
# #41, @AssertTrue) - sans ce champ, /api/users/register renvoie desormais 400
# et TOUT le reste du script echoue en cascade (UID_A/UID_B jamais peuples).
# ------------------------------------------------------------------------------

TRAVELER_A=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Alice\",\"lastName\":\"TravelerA\",\"email\":\"alice.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
UID_A=$(extract_field "$TRAVELER_A" id)
REG_TOKEN_A=$(extract_field "$TRAVELER_A" registrationToken)

TOKEN_A_RAW=$(curl -sk -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"alice_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_A\"}")
TOKEN_A=$(extract_field "$TOKEN_A_RAW" token)

TRAVELER_B=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Bob\",\"lastName\":\"TravelerB\",\"email\":\"bob.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
UID_B=$(extract_field "$TRAVELER_B" id)
REG_TOKEN_B=$(extract_field "$TRAVELER_B" registrationToken)

TOKEN_B_RAW=$(curl -sk -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"bob_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_B\"}")
TOKEN_B=$(extract_field "$TOKEN_B_RAW" token)

MANAGER_PROFILE=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Mia\",\"lastName\":\"Manager\",\"email\":\"mia.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
UID_MGR=$(extract_field "$MANAGER_PROFILE" id)

ADMIN_LOGIN_RAW=$(curl -sk -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"changeme_dev_only"}')
ADMIN_TOKEN=$(extract_field "$ADMIN_LOGIN_RAW" token)

curl -sk -X POST $BASE/api/auth/accounts -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"username\":\"mia_manager_$SUFFIX\",\"password\":\"Password1234\",\"role\":\"TRAVEL_MANAGER\",\"userId\":\"$UID_MGR\"}" > /dev/null

MANAGER_LOGIN_RAW=$(curl -sk -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"mia_manager_$SUFFIX\",\"password\":\"Password1234\"}")
MANAGER_TOKEN=$(extract_field "$MANAGER_LOGIN_RAW" token)

echo "  UID_A=$UID_A  UID_B=$UID_B  UID_MGR=$UID_MGR"
if [ -z "$MANAGER_TOKEN" ] || [ -z "$TOKEN_A" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "  [FAIL] setup incomplet (un token est vide) - le reste du script va echouer en cascade."
    echo "         Verifie que tous les services sont up (docker compose ps) avant de relancer."
fi

# ------------------------------------------------------------------------------
section "SETUP — creation de trois voyages (lointain, imminent, deja termine)"
# QUOI : un voyage loin dans le futur (annulation d'abonnement libre), un
# voyage qui commence demain (dans la fenetre des 3 jours de coupure), et un
# voyage DEJA TERMINE (FeedbackService.submit refuse tout feedback tant que
# LocalDate.now().isBefore(travel.getEndDate()) - donc un feedback ne peut
# etre teste que sur un voyage dont la date de fin est deja passee).
# ------------------------------------------------------------------------------

TRAVEL_FAR=$(curl -sk -X POST $BASE/api/travels -H "Content-Type: application/json" -H "Authorization: Bearer $MANAGER_TOKEN" -d '{"title":"Voyage lointain","startDate":"2027-06-01","endDate":"2027-06-10","status":"PLANNED","price":100,"currency":"EUR","destinations":[{"city":"Paris","country":"France","arrivalDate":"2027-06-01","departureDate":"2027-06-10","orderIndex":0}]}')
TRAVEL_FAR_ID=$(echo "$TRAVEL_FAR" | grep -o '"id":"[^"]*"' | head -n1 | cut -d'"' -f4)

TOMORROW=$(date -u -d "+1 day" +%Y-%m-%d 2>/dev/null || date -u -v+1d +%Y-%m-%d)
IN_5_DAYS=$(date -u -d "+5 day" +%Y-%m-%d 2>/dev/null || date -u -v+5d +%Y-%m-%d)
TRAVEL_SOON=$(curl -sk -X POST $BASE/api/travels -H "Content-Type: application/json" -H "Authorization: Bearer $MANAGER_TOKEN" -d "{\"title\":\"Voyage imminent\",\"startDate\":\"$TOMORROW\",\"endDate\":\"$IN_5_DAYS\",\"status\":\"PLANNED\",\"price\":50,\"currency\":\"EUR\",\"destinations\":[{\"city\":\"Lyon\",\"country\":\"France\",\"arrivalDate\":\"$TOMORROW\",\"departureDate\":\"$IN_5_DAYS\",\"orderIndex\":0}]}")
TRAVEL_SOON_ID=$(echo "$TRAVEL_SOON" | grep -o '"id":"[^"]*"' | head -n1 | cut -d'"' -f4)

TRAVEL_PAST=$(curl -sk -X POST $BASE/api/travels -H "Content-Type: application/json" -H "Authorization: Bearer $MANAGER_TOKEN" -d '{"title":"Voyage termine","startDate":"2026-01-01","endDate":"2026-01-10","status":"COMPLETED","price":80,"currency":"EUR","destinations":[{"city":"Nice","country":"France","arrivalDate":"2026-01-01","departureDate":"2026-01-10","orderIndex":0}]}')
TRAVEL_PAST_ID=$(echo "$TRAVEL_PAST" | grep -o '"id":"[^"]*"' | head -n1 | cut -d'"' -f4)

echo "  TRAVEL_FAR_ID=$TRAVEL_FAR_ID   TRAVEL_SOON_ID=$TRAVEL_SOON_ID (demarre le $TOMORROW)   TRAVEL_PAST_ID=$TRAVEL_PAST_ID"

# SubscriptionController.subscribe() renvoie 201 + le SubscriptionResponse cree -
# il faut recuperer SON id (different du travel id) pour pouvoir l'annuler ensuite
# (DELETE .../subscriptions/{subscriptionId}, pas .../subscriptions tout court).
SUB_FAR=$(curl -sk -X POST $BASE/api/travels/$TRAVEL_FAR_ID/subscriptions -H "Authorization: Bearer $TOKEN_A")
SUB_FAR_ID=$(extract_field "$SUB_FAR" id)
SUB_SOON=$(curl -sk -X POST $BASE/api/travels/$TRAVEL_SOON_ID/subscriptions -H "Authorization: Bearer $TOKEN_A")
SUB_SOON_ID=$(extract_field "$SUB_SOON" id)
# FeedbackService exige une subscription existante (peu importe son statut) sur CE
# voyage precis pour A comme pour B - les deux doivent etre abonnes au voyage termine.
curl -sk -o /dev/null -X POST $BASE/api/travels/$TRAVEL_PAST_ID/subscriptions -H "Authorization: Bearer $TOKEN_A"
curl -sk -o /dev/null -X POST $BASE/api/travels/$TRAVEL_PAST_ID/subscriptions -H "Authorization: Bearer $TOKEN_B"
echo "  SUB_FAR_ID=$SUB_FAR_ID   SUB_SOON_ID=$SUB_SOON_ID"

# ------------------------------------------------------------------------------
section "TEST 1 — IDOR sur GET /api/users/{id} (troubleshooting.md #38)"
# QUOI : un manager consulte le profil d'un traveler ABONNE a l'un de ses
# voyages, puis celui d'un traveler NON abonne.
# COMMENT : GET /api/users/{UID} avec le token du manager.
# POURQUOI ce resultat : UserService.findById() appelle desormais
# TravelServiceClient.isSubscriberOfCallingManager avant de renvoyer le profil
# (ADMIN passe toujours, TRAVEL_MANAGER doit avoir une relation d'abonnement
# reelle) - avant le fix, les deux appels renvoyaient 200 sans distinction.
# ------------------------------------------------------------------------------

CODE=$(curl -sk -o /dev/null -w "%{http_code}" $BASE/api/users/$UID_A -H "Authorization: Bearer $MANAGER_TOKEN")
check_status "manager -> profil d'un ABONNE" 200 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" $BASE/api/users/$UID_B -H "Authorization: Bearer $MANAGER_TOKEN")
check_status "manager -> profil d'un NON-abonne" 403 "$CODE"

# ------------------------------------------------------------------------------
section "TEST 2 — controle de role : un TRAVELER ne peut pas agir comme un ADMIN/MANAGER"
# QUOI : traveler A tente de creer un voyage (reserve ADMIN/TRAVEL_MANAGER) et
# de lister tous les signalements (reserve ADMIN).
# POURQUOI : SecurityConfig de travel-service verrouille ces routes par role
# AVANT meme d'atteindre le service - independant de tout controle de relation.
# ------------------------------------------------------------------------------

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/travels -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d '{"title":"Hack","startDate":"2027-01-01","endDate":"2027-01-02","status":"PLANNED","price":1,"currency":"EUR","destinations":[]}')
check_status "traveler -> POST /api/travels (creation voyage)" 403 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" $BASE/api/reports -H "Authorization: Bearer $TOKEN_A")
check_status "traveler -> GET /api/reports (liste globale, ADMIN only)" 403 "$CODE"

# ------------------------------------------------------------------------------
section "TEST 3 — un traveler relit le CONTENU de son propre feedback/reports (troubleshooting.md #40.6)"
# QUOI : traveler A soumet un feedback sur le voyage lointain, puis relit la
# liste de SES PROPRES feedbacks (endpoint ajoute le 26/08 - avant, seul un
# COMPTEUR etait accessible au traveler, jamais le contenu).
# ------------------------------------------------------------------------------

curl -sk -o /dev/null -X POST $BASE/api/travels/$TRAVEL_PAST_ID/feedbacks -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d '{"rating":5,"comment":"Super voyage"}'

MY_FEEDBACKS=$(curl -sk -w "\nHTTP_CODE:%{http_code}" $BASE/api/travels/travelers/me/feedbacks -H "Authorization: Bearer $TOKEN_A")
CODE=$(echo "$MY_FEEDBACKS" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
check_status "traveler -> GET /api/travels/travelers/me/feedbacks" 200 "$CODE"
echo "$MY_FEEDBACKS" | grep -q "Super voyage" && { echo "  [OK]   le contenu du feedback est bien present dans la reponse"; PASS=$((PASS+1)); } || { echo "  [FAIL] le feedback soumis n'apparait pas dans la reponse"; FAIL=$((FAIL+1)); }

# ------------------------------------------------------------------------------
section "TEST 4 — annulation d'abonnement et delai de 3 jours (SubscriptionService.CANCELLATION_CUTOFF_DAYS)"
# QUOI : annulation d'un voyage LOINTAIN (doit reussir) vs d'un voyage qui
# commence DEMAIN, donc a l'interieur de la fenetre de 3 jours (doit echouer).
# ------------------------------------------------------------------------------

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X DELETE $BASE/api/travels/$TRAVEL_FAR_ID/subscriptions/$SUB_FAR_ID -H "Authorization: Bearer $TOKEN_A")
check_status "annulation d'un voyage lointain (hors delai de coupure)" 204 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X DELETE $BASE/api/travels/$TRAVEL_SOON_ID/subscriptions/$SUB_SOON_ID -H "Authorization: Bearer $TOKEN_A")
check_status "annulation d'un voyage a moins de 3 jours (doit etre refusee, SubscriptionCutoffException -> 409)" 409 "$CODE"

# ------------------------------------------------------------------------------
section "TEST 5 — headers de securite HTTP (troubleshooting.md #40.2)"
# QUOI : la reponse HTTPS doit porter les 4 headers ajoutes le 26/08.
# ------------------------------------------------------------------------------

HEADERS=$(curl -sk -I $BASE/api/travels)
for H in "X-Content-Type-Options" "X-Frame-Options" "Referrer-Policy" "Strict-Transport-Security" "Content-Security-Policy"; do
    if echo "$HEADERS" | grep -qi "$H"; then
        echo "  [OK]   header present : $H"
        PASS=$((PASS+1))
    else
        echo "  [FAIL] header ABSENT : $H"
        FAIL=$((FAIL+1))
    fi
done

# ------------------------------------------------------------------------------
section "TEST 6 — rate-limit sur POST /api/auth/login (troubleshooting.md #40.1)"
# QUOI : 8 tentatives de connexion rapides avec un mauvais mot de passe.
# POURQUOI : limit_req_zone=5r/m + burst=3 -> les toutes premieres passent
# (401, mauvais mot de passe) puis nginx doit repondre 429 une fois le burst
# depasse. On ne verifie PAS le contenu (401 vs 429), seulement qu'un 429
# apparait bien a un moment (preuve que la limite existe et s'applique).
# ------------------------------------------------------------------------------

GOT_429=0
for i in $(seq 1 8); do
    CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong_password"}')
    echo "  tentative $i -> $CODE"
    [ "$CODE" = "429" ] && GOT_429=1
done
if [ "$GOT_429" = "1" ]; then
    echo "  [OK]   429 observe avant la fin des 8 tentatives -> le rate-limit fonctionne"
    PASS=$((PASS+1))
else
    echo "  [FAIL] jamais de 429 sur 8 tentatives rapides -> le rate-limit ne s'est pas declenche (relance le test, ou verifie que nginx a bien ete rebuild avec nginx-main.conf)"
    FAIL=$((FAIL+1))
fi

# ------------------------------------------------------------------------------
section "TEST 7 — sonde d'injection SQL (sanite, pas exhaustif)"
# QUOI : un payload SQLi classique en parametre de recherche.
# POURQUOI ca doit passer : toutes les requetes du projet passent par des
# methodes derivees Spring Data JPA (parametrees nativement, jamais de SQL
# concatene) - un payload SQLi doit etre traite comme une chaine de recherche
# normale (0 resultat probable), jamais provoquer une 500 ni renvoyer plus de
# resultats que prevu.
# ------------------------------------------------------------------------------

# GET /api/travels/** exige une authentification TRAVELER minimum (SecurityConfig) -
# le payload doit donc etre envoye avec un token valide, sinon on ne teste que le 401.
SQLI_RESP=$(curl -sk -w "\nHTTP_CODE:%{http_code}" -G $BASE/api/travels/search -H "Authorization: Bearer $TOKEN_A" --data-urlencode "q=' OR '1'='1")
CODE=$(echo "$SQLI_RESP" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
if [ "$CODE" = "200" ] || [ "$CODE" = "400" ]; then
    echo "  [OK]   payload SQLi traite normalement (code $CODE, pas de 500)"
    PASS=$((PASS+1))
else
    echo "  [FAIL] code inattendu sur payload SQLi : $CODE (a inspecter manuellement, pas forcement une faille mais merite un coup d'oeil)"
    FAIL=$((FAIL+1))
fi

# ------------------------------------------------------------------------------
section "TEST 8 — sonde XSS stocke (feedback contenant un script)"
# QUOI : soumission d'un feedback dont le commentaire contient une balise
# <script>. COMMENT : le backend doit l'accepter/stocker tel quel (ce n'est
# pas son role de filtrer - Spring ne fait pas d'echappement HTML) SANS
# planter. La vraie protection contre l'execution de ce script est cote
# frontend (Angular echappe tout binding par defaut) - CE SCRIPT NE PEUT PAS
# tester ca via curl seul : verifie a l'oeil, dans l'UI, que le texte
# "<script>alert(1)</script>" s'affiche tel quel en TEXTE dans la page de
# feedback du voyage, sans jamais declencher de popup.
# ------------------------------------------------------------------------------

XSS_RESP=$(curl -sk -w "\nHTTP_CODE:%{http_code}" -X POST $BASE/api/travels/$TRAVEL_PAST_ID/feedbacks -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_B" -d '{"rating":1,"comment":"<script>alert(1)</script>"}')
CODE=$(echo "$XSS_RESP" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
check_status "soumission d'un feedback contenant une balise <script> (ne doit pas planter)" 201 "$CODE"
echo "  -> ETAPE MANUELLE OBLIGATOIRE : ouvre l'UI (dashboard manager du voyage termine), verifie que ce commentaire s'affiche en texte brut, jamais execute."

# ------------------------------------------------------------------------------
section "TEST 9 — latence indicative (PAS un test de charge)"
# QUOI : temps de reponse sur 3 endpoints cles, un seul utilisateur a la fois.
# POURQUOI : le point d'audit sur la charge demande <5s par action ET une
# tenue en charge sous forte affluence - ceci verifie seulement le premier
# point en conditions IDEALES (aucune charge concurrente). Un vrai test de
# charge (k6/JMeter, plusieurs dizaines d'utilisateurs simultanes) reste a
# faire separement pour repondre completement a ce point.
# ------------------------------------------------------------------------------

for EP in "/api/travels" "/api/travels/search?q=paris"; do
    T=$(curl -sk -o /dev/null -w "%{time_total}" -H "Authorization: Bearer $TOKEN_A" $BASE$EP)
    echo "  $EP -> ${T}s"
done
T=$(curl -sk -o /dev/null -w "%{time_total}" $BASE/)
echo "  / (frontend) -> ${T}s"

# ------------------------------------------------------------------------------
section "TEST 10 — conformite protection des donnees / RGPD (troubleshooting.md #41)"
# QUOI : consentement obligatoire a l'inscription, droit d'acces (GET /me),
# droit a l'effacement (DELETE /me) qui doit supprimer a la fois le profil
# ET le compte de connexion - verifie en tentant un login APRES suppression
# (pas juste en verifiant le code HTTP de la suppression elle-meme, qui ne
# prouve rien sur ce qui a reellement ete supprime cote auth-service).
# Utilise un traveler JETABLE (Carol) plutot que A/B, pour ne pas casser les
# tests precedents en supprimant un compte encore utilise ailleurs.
# ------------------------------------------------------------------------------

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Carol\",\"lastName\":\"NoConsent\",\"email\":\"carol.noconsent.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":false}")
check_status "inscription SANS consentement (acceptedPrivacyPolicy:false)" 400 "$CODE"

CAROL_PROFILE=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Carol\",\"lastName\":\"Deletable\",\"email\":\"carol.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
UID_CAROL=$(extract_field "$CAROL_PROFILE" id)
REG_TOKEN_CAROL=$(extract_field "$CAROL_PROFILE" registrationToken)
CAROL_TOKEN_RAW=$(curl -sk -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"carol_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_CAROL\"}")
CAROL_TOKEN=$(extract_field "$CAROL_TOKEN_RAW" token)
echo "  UID_CAROL=$UID_CAROL"

ME_RESP=$(curl -sk -w "\nHTTP_CODE:%{http_code}" $BASE/api/users/me -H "Authorization: Bearer $CAROL_TOKEN")
CODE=$(echo "$ME_RESP" | grep -o 'HTTP_CODE:[0-9]*' | cut -d: -f2)
check_status "droit d'acces : GET /api/users/me" 200 "$CODE"
echo "$ME_RESP" | grep -q "carol.$SUFFIX@example.com" && { echo "  [OK]   le profil retourne est bien celui de l'appelant"; PASS=$((PASS+1)); } || { echo "  [FAIL] email inattendu dans la reponse /me"; FAIL=$((FAIL+1)); }

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X DELETE $BASE/api/users/me -H "Authorization: Bearer $CAROL_TOKEN")
check_status "droit a l'effacement : DELETE /api/users/me" 204 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"carol_$SUFFIX\",\"password\":\"Password1234\"}")
check_status "login APRES suppression (le compte auth-service doit etre vraiment supprime, pas seulement le profil)" 401 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" $BASE/api/users/$UID_CAROL -H "Authorization: Bearer $ADMIN_TOKEN")
check_status "GET du profil supprime par un admin (doit etre introuvable)" 404 "$CODE"

# ------------------------------------------------------------------------------
section "TEST 11 — prise de controle de compte via POST /api/auth/register"
# Un jeton invente doit etre rejete, et un jeton valide deja consomme ne doit
# jamais pouvoir etre rejoue sous un autre username.
# ------------------------------------------------------------------------------

DAVE_PROFILE=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Dave\",\"lastName\":\"TakeoverTarget\",\"email\":\"dave.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
REG_TOKEN_DAVE=$(extract_field "$DAVE_PROFILE" registrationToken)

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"attacker_forged_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"ce-nest-pas-un-jwt-valide\"}")
check_status "jeton d'inscription invente (pas un JWT) -> rejete" 400 "$CODE"

CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"dave_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_DAVE\"}")
check_status "inscription LEGITIME de Dave avec son propre jeton" 201 "$CODE"

# Rejeu du meme jeton (toujours valide) sous un autre username - doit etre bloque
# par la contrainte UNIQUE sur accounts.user_id.
CODE=$(curl -sk -o /dev/null -w "%{http_code}" -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"attacker_replay_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_DAVE\"}")
check_status "rejeu du jeton de Dave sous un autre username (prise de controle de compte) -> rejete" 409 "$CODE"

# ------------------------------------------------------------------------------
section "RESUME"
echo "  PASS=$PASS  FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
    echo "  Tous les tests attendus sont au vert. Il reste les etapes manuelles signalees ci-dessus (XSS visuel, vrai test de charge)."
else
    echo "  $FAIL test(s) en echec - relis la section correspondante ci-dessus avant de conclure a un vrai bug (un service pas encore demarre donne souvent un faux echec en cascade)."
fi
