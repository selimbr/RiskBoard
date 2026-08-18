# TODO / Pistes d'amélioration

Liste des points non traités ou simplifiés faute de temps, ainsi que des évolutions
possibles pour un passage en production.

## Hors périmètre (explicitement exclu par l'énoncé)

- Authentification / autorisation (RBAC Sales vs Risk Manager, etc.)

## Backend

- **Agrégation multi-devises** : `aggregateExposureBySector` additionne les
  `usedAmount` sans tenir compte de la devise (`currency`). Sur le jeu de données
  fourni ce n'est pas neutre (EUR, USD, GBP, CHF mélangés) — une vraie implémentation
  demanderait une conversion vers une devise pivot avant agrégation.
- **Import CSV non streamé** : le fichier est chargé en mémoire et chaque ligne
  déclenche des allers-retours DB individuels (pas de batch insert). Suffisant pour
  quelques milliers de lignes, à revoir pour des fichiers volumineux.
- **Pas de pagination côté serveur** sur `/api/risk-limits/dashboard` et
  `/api/counterparties` : tout est renvoyé en une fois, la pagination/le tri/le
  filtre sont faits côté client. À revoir si le volume de contreparties devient
  important.
- **H2 fichier plutôt que PostgreSQL** : suffisant pour le test, mais H2 n'est pas
  fait pour la production (pas de vraie gestion de la concurrence, migration de
  schéma via `ddl-auto=update` à remplacer par Flyway/Liquibase).
- **Pas d'API documentée (OpenAPI/Swagger)**.
- **Pas de logs structurés ni d'endpoint de santé** (Spring Boot Actuator).
- **Pas de verrouillage optimiste** sur `RiskLimit` en cas d'imports concurrents.

## Frontend

- **Pas d'écran listant l'historique complet des dérogations** (seules les demandes
  `PENDING` sont affichées ; `APPROVED`/`REJECTED` disparaissent de la vue une fois
  traitées côté UI, bien qu'elles restent en base).
- **Pas de confirmation avant Valider/Rejeter** une dérogation (action immédiate).
- **Pas de tests unitaires sur `dashboard.ts`** — `derogation-form.ts` et
  `csv-upload.ts` sont couverts, `dashboard.ts` (tri multi-critères, filtre,
  pagination) ne l'est pas encore.
- **Pas de tests end-to-end** (Cypress/Playwright) validant le parcours complet.
- **Pas de notifications toast** : les retours utilisateur (succès/erreur) sont de
  simples messages inline.

## Infra / CI

- Le pipeline `.gitlab-ci.yml` build et teste, mais ne publie pas d'image Docker
  (pas de stage `deploy`/`release`).
- Pas de healthcheck Docker Compose sur les services.
- Pas de scan de vulnérabilités (dependency scanning) dans la CI.
