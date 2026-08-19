path "secret/data/auth-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/auth-service/*" {
  capabilities = ["list", "read"]
}
