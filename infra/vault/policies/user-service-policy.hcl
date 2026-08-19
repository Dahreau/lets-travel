path "secret/data/user-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/user-service/*" {
  capabilities = ["list", "read"]
}
