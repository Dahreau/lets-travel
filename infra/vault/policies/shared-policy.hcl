path "secret/data/shared/*" {
  capabilities = ["read"]
}

path "secret/metadata/shared/*" {
  capabilities = ["list", "read"]
}
