#!/bin/sh
set -e

SERVICES="api-gateway auth-service user-service travel-service payment-service"

if ! vault auth list -format=json | grep -q '"approle/"'; then
	vault auth enable approle
fi

if ! vault secrets list -format=json | grep -q '"secret/"'; then
	vault secrets enable -path=secret kv-v2
fi

vault policy write "shared-policy" "/vault-init/policies/shared-policy.hcl"

for svc in $SERVICES; do
	vault policy write "${svc}-policy" "/vault-init/policies/${svc}-policy.hcl"

	vault write "auth/approle/role/${svc}" \
		token_policies="${svc}-policy,shared-policy" \
		token_ttl=1h \
		token_max_ttl=4h

	echo "--- ${svc} ---"
	echo "role_id:"
	vault read -field=role_id "auth/approle/role/${svc}/role-id"
done

if ! vault kv get secret/shared/jwt >/dev/null 2>&1; then
	jwt_secret=$(vault write -field=random_bytes sys/tools/random/32 format=base64)
	vault kv put secret/shared/jwt secret="$jwt_secret"
	echo "Secret JWT partage cree dans secret/shared/jwt"
fi

if ! vault kv get secret/payment-service/stripe >/dev/null 2>&1; then
	vault kv put secret/payment-service/stripe secret_key="${STRIPE_SECRET_KEY:-sk_test_changeme_dev_only}"
	echo "Secret Stripe cree dans secret/payment-service/stripe"
fi

if ! vault kv get secret/payment-service/paypal >/dev/null 2>&1; then
	vault kv put secret/payment-service/paypal \
		client_id="${PAYPAL_CLIENT_ID:-changeme_dev_only}" \
		client_secret="${PAYPAL_CLIENT_SECRET:-changeme_dev_only}"
	echo "Secret PayPal cree dans secret/payment-service/paypal"
fi

echo "Vault bootstrap done: AppRole enabled, one policy + one role per service, shared JWT/Stripe/PayPal secrets seeded."
