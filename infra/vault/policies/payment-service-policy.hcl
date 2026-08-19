path "secret/data/payment-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/payment-service/*" {
  capabilities = ["list", "read"]
}
