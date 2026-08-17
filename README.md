# RiskBoard

Application de suivi des limites de risque des contreparties pour les équipes Sales
d'une banque : dashboard temps réel, import CSV, et workflow de demandes de dérogation.

- **Backend** : Java 21, Spring Boot 4, Spring Data JPA, H2
- **Frontend** : Angular 21 (standalone components, signals), Reactive Forms
- **Base de données** : H2 (fichier local, aucune installation requise)

## Prérequis

- Java 21+
- Maven (le wrapper `mvnw` est fourni, pas besoin de Maven installé globalement)
- Node.js 20+ et npm
- Docker & Docker Compose (optionnel, pour lancer l'ensemble en conteneurs)

## Lancer en local (sans Docker)

### Backend

```bash
cd riskBoard
./mvnw spring-boot:run
```

Le backend démarre sur `http://localhost:8080`. La base H2 est stockée dans
`riskBoard/data/riskboard.mv.db` (créée automatiquement au premier lancement, schéma
auto-généré via `spring.jpa.hibernate.ddl-auto=update`).

La base est **vide au démarrage** : il faut importer un fichier CSV (voir plus bas)
pour peupler les contreparties et leurs limites.

### Frontend

Dans un second terminal :

```bash
cd frontend
npm install
npm start
```

Le frontend démarre sur `http://localhost:4200` et appelle le backend sur
`http://localhost:8080/api` (URL codée en dur dans
`frontend/src/app/core/api-base-url.ts`, à adapter si besoin).

## Lancer avec Docker Compose

```bash
docker compose up --build
```

- Backend : `http://localhost:8080`
- Frontend : `http://localhost:4200`

Les données H2 sont persistées dans un volume Docker nommé `riskboard-data`.

## Importer des données

Un jeu de données de test est fourni dans
[`sample-data/risk-limits-sample.csv`](sample-data/risk-limits-sample.csv) (le même
que celui de l'énoncé, couvrant les trois niveaux d'alerte GREEN/ORANGE/RED).

Deux façons de l'importer :

1. **Via l'interface** : onglet "Import CSV" dans l'application, sélectionner le
   fichier puis cliquer sur "Importer".
2. **Via curl** :

```bash
curl -X POST http://localhost:8080/api/import/risk-limits \
  -F "file=@sample-data/risk-limits-sample.csv"
```

Le format attendu du CSV :

```
name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency
```

L'import est tolérant aux erreurs : une ligne invalide est ignorée et rapportée dans
la réponse (`errors`), sans bloquer les autres lignes.

## Lancer les tests

### Backend

```bash
cd riskBoard
./mvnw test
```

Tests couvrant : le calcul du taux d'usage et des niveaux d'alerte (GREEN/ORANGE/RED),
l'agrégation d'exposition par secteur, et l'import CSV (succès partiel + erreurs).

### Frontend

```bash
cd frontend
npm test
```

## CI (GitHub Actions)

Le workflow GitHub Actions (`.github/workflows/ci.yml`) s'exécute à chaque push sur
`main` (validation après merge), ainsi qu'à la création/réouverture des pull
requests vers `main`.

Pour visualiser les étapes build/test backend et frontend, ouvrir l'onglet
**Actions** du dépôt GitHub puis sélectionner un run du workflow **CI**.

## Structure du dépôt

```
riskBoard/               racine du dépôt
├── riskBoard/            backend Spring Boot
│   └── src/main/java/fr/riskBoard/
│       ├── domain/       entités JPA + enums
│       ├── repository/   Spring Data JPA
│       ├── service/      logique métier
│       ├── controller/   endpoints REST
│       ├── dto/          objets d'échange API
│       └── exception/    gestion d'erreurs centralisée
├── frontend/              application Angular
│   └── src/app/
│       ├── core/          services, modèles partagés
│       └── features/      un dossier par écran (dashboard, upload, derogation-form, derogation-pending)
├── sample-data/           jeu de données CSV de test
├── docker-compose.yml
├── .gitlab-ci.yml
└── README.md
```

## API principale

| Méthode | Endpoint | Description |
|---|---|---|
| GET | `/api/counterparties` | Liste des contreparties |
| GET | `/api/risk-limits/dashboard` | Données du tableau de bord (une ligne par limite) |
| GET | `/api/risk-limits/aggregation?limitType=CREDIT` | Exposition agrégée par secteur pour un type de limite |
| GET | `/api/risk-limits/aggregation/sector` | Exposition agrégée par secteur (tous types confondus) |
| GET | `/api/risk-limits/counterparty/{id}/type/{limitType}` | Détail d'une limite (404 si inexistante) |
| POST | `/api/import/risk-limits` | Import CSV (multipart) |
| POST | `/api/derogations` | Créer une demande de dérogation |
| GET | `/api/derogations/pending` | Demandes en attente |
| POST | `/api/derogations/{id}/approve` | Valider une demande |
| POST | `/api/derogations/{id}/reject` | Rejeter une demande |
