# Keycloak

Identity provider for QuickBite: users log in here and get a JWT access token that the gateway and
every service validate. Realm `quickbite`, public client `quickbite-app`, realm roles `admin`,
`customer`, `restaurant`, `driver`.

| File | Used by |
|------|---------|
| `realm/quickbite-realm.json` | Kubernetes prod overlay: realm, roles and client, **no users** (create them in the admin console) |
| `dev-users/quickbite-realm.json` | Docker Compose and the local Kubernetes overlay: the same realm plus test users |
| `kustomization.yaml`, `k8s/` | Kustomize component: Keycloak Deployment + Service, realm ConfigMap, token settings for the services |
| `dev-users/kustomization.yaml` | Kustomize component: swaps in the realm with test users (local overlay only) |

Keep the two realm files identical apart from `users`. Keycloak imports the realm on first start
only (an existing realm is left as it is); delete the realm in the admin console, or the
`quickbite_keycloak_db` database, to re-import it.

Test users (development only, password `quickbite123` for all): `admin`, `customer1`, `customer2`,
`restaurant1`, `driver1`, each with the role of the same name.
