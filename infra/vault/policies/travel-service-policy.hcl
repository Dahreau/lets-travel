path "secret/data/travel-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/travel-service/*" {
  capabilities = ["list", "read"]
}
