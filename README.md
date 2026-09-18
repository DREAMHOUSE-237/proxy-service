# proxy-service

Passerelle API (API Gateway) de la plateforme **DREAMHOUSE237**, développée avec **Spring Cloud Gateway**.

## Rôle

Point d'entrée unique de tout le trafic backend. Route chaque requête vers le microservice adéquat en fonction du préfixe d'URL, en s'appuyant sur `registry-service` (Eureka) pour la découverte des instances (load-balancing round-robin via Spring Cloud LoadBalancer).

## Routes principales

| Préfixe | Service cible |
|---|---|
| `/AUTHENTIFICATION/**` | auth-service |
| `/USER-SERVICE/**` | user-service |
| `/PUBLICATION-SERVICE/**` | publication-service |
| `/COMMENTARY-SERVICE/**` | commentary-service |
| `/PAYMENT-SERVICE/**` et `/payment-service/**` | payment-service (la variante minuscule est dédiée au webhook Campay) |
| `/IDENTITY-SERVICE/**` et `/identity-service/**` | identity-verification-service |

Chaque route réécrit le chemin (`RewritePath`) avant de le transmettre au service cible.

## Stack

- Java / Spring Cloud Gateway
- Port interne `8080`
- Exposé publiquement via **Traefik** (TLS Let's Encrypt, domaine `nip.io`)

## Architecture & découverte de service

S'enregistre auprès de `registry-service` (Eureka) et récupère sa configuration (routes, hostname) depuis `config-service`. C'est le seul service backend directement exposé à Internet (via Traefik) — tous les autres microservices ne sont accessibles que via lui, à l'intérieur du réseau overlay Docker Swarm.

## Développement local

```bash
./mvnw spring-boot:run
```

## Déploiement

Via **Docker Swarm** (voir [`infrastructure`](https://github.com/DREAMHOUSE-237/infrastructure)). Un push sur `dev` déclenche le pipeline CI/CD complet jusqu'au redéploiement en production.
