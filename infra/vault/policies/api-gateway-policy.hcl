path "secret/data/api-gateway/*" {
  capabilities = ["read"]
}

path "secret/metadata/api-gateway/*" {
  capabilities = ["list", "read"]
}
