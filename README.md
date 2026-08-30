# 🏥 MediLabo — Deployment

Ce dépôt contient les fichiers nécessaires au **déploiement et aux tests d'intégration de MediLabo avec Docker Compose**.

MediLabo est composée de plusieurs services :

* **Cloud Gateway** : point d'entrée de l'API
* **Infos Patient** : gestion des informations patient
* **Notes Patient** : gestion des notes médicales
* **Risque Diabète Patient** : calcul du niveau de risque
* **Frontend** : interface utilisateur
* **PostgreSQL** : bases de données des services Infos Patient et Risque Diabète
* **MongoDB** : base de données des notes patient

Le projet utilise notamment :

* Docker
* Docker Compose
* GitHub Container Registry (GHCR)
* Jenkins
* Testcontainers
* JUnit
* RestAssured

---

# 📐 Architecture

```text id="v0c2zq"
                              ┌───────────────────┐
                              │      Frontend      │
                              │       :9081        │
                              └─────────┬─────────┘
                                        │
                                        ▼
                              ┌───────────────────┐
                              │   Cloud Gateway   │
                              │       :9080        │
                              └─────────┬─────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
                    ▼                   ▼                   ▼
             ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
             │ Infos Patient│   │Notes Patient │   │Risque Diabète│
             │    :8080     │   │    :8080     │   │    :8080     │
             └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
                    │                  │                   │
                    ▼                  ▼                   ▼
             ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
             │  PostgreSQL  │   │   MongoDB    │   │  PostgreSQL  │
             │    :5432     │   │    :27017    │   │    :5432     │
             └──────────────┘   └──────────────┘   └──────────────┘
```

Les services communiquent au sein du réseau Docker :

```text id="h3g5jb"
medilabo
```

Le Gateway constitue le point d'entrée de l'API.

---

# 📁 Organisation des projets

Pour utiliser les configurations locales, les projets doivent être organisés de la manière suivante :

```text id="v5b9qf"
MediLabo/
│
├── cloudGateway/
├── deployment/
├── front-medilabo/
├── infosPatients/
├── notesPatients/
└── risqueDiabetePatients/
```

Correspondance :

| Répertoire               | Projet                             |
| ------------------------ | ---------------------------------- |
| `cloudGateway/`          | Cloud Gateway                      |
| `deployment/`            | Déploiement et tests d'intégration |
| `front-medilabo/`        | Frontend                           |
| `infosPatients/`         | Service Infos Patient              |
| `notesPatients/`         | Service Notes Patient              |
| `risqueDiabetePatients/` | Service Risque Diabète             |

Les fichiers Compose locaux utilisent cette organisation avec des chemins `build.context` relatifs.

---

# 🐳 Docker Compose

Cinq fichiers Compose sont disponibles :

| Fichier                         | Utilisation                                    |
| ------------------------------- | ---------------------------------------------- |
| `docker-compose.yml`            | 🚀 Déploiement en production                   |
| `docker-compose.test.yml`       | 🧪 Tests d'intégration Jenkins                 |
| `docker-compose.demo.yml`       | 🎬 Démonstration et référence de configuration |
| `docker-compose.local.yml`      | 🖥️ Déploiement local                          |
| `docker-compose.test.local.yml` | 🧪 Tests d'intégration locaux                  |

---

# 🚀 Production — `docker-compose.yml`

Ce fichier est utilisé pour le **déploiement réel en production**.

Les images applicatives sont récupérées depuis GHCR :

```text id="zv8j7h"
ghcr.io/braseb-medilabo/patient-info-backend
ghcr.io/braseb-medilabo/patient-note-backend
ghcr.io/braseb-medilabo/patient-risque-diabete-backend
ghcr.io/braseb-medilabo/gateway
ghcr.io/braseb-medilabo/front-end
```

Les versions des images sont fournies par les variables :

```text id="oy7t4s"
INFO_PATIENT_VERSION
NOTE_PATIENT_VERSION
RISK_PATIENT_VERSION
GATEWAY_VERSION
FRONT_VERSION
```

La configuration complète des variables d'environnement est documentée dans les README des services concernés.

## Démarrage

```bash
docker compose up -d
```

## Télécharger les nouvelles images puis redémarrer

```bash
docker compose pull
docker compose up -d
```

## Recréer les conteneurs

```bash
docker compose up -d --force-recreate
```

## Recréer les conteneurs avec les nouvelles images

```bash
docker compose pull
docker compose up -d --force-recreate
```

## Arrêt

```bash
docker compose down
```

Les volumes persistants ne sont pas supprimés par cette commande.

## Arrêt et suppression des volumes

```bash
docker compose down -v
```

⚠️ Cette commande supprime également les données stockées dans les volumes Docker.

---

# 🧪 Tests d'intégration Jenkins — `docker-compose.test.yml`

Ce fichier est utilisé par Jenkins pour exécuter les **tests d'intégration avant le déploiement en production**.

Les services utilisent les images Docker publiées sur GHCR.

Les tests sont exécutés avec **Testcontainers**.

Le projet de tests se trouve dans :

```text
deployment/
└── integration.test/
```

La classe principale de test est :

```text
GatewayIT
```

---

## Fonctionnement

Jenkins récupère automatiquement les dernières versions disponibles des images Docker.

Pour chaque service, Jenkins :

1. récupère un token GHCR ;
2. récupère la liste des tags ;
3. conserve uniquement les tags au format `MAJOR.MINOR.PATCH` ;
4. sélectionne la dernière version ;
5. stocke cette version dans une variable d'environnement ;
6. génère le `.env` utilisé par Docker Compose ;
7. télécharge les images ;
8. lance les tests d'intégration.

Les services concernés sont :

```text id="h0uzr5"
patient-info-backend
patient-note-backend
patient-risque-diabete-backend
gateway
front-end
```

---

# 🔄 Pipeline Jenkins

Le fonctionnement global est :

```text id="k9w3sa"
               GHCR
                │
                ▼
        ┌────────────────┐
        │     Jenkins    │
        └───────┬────────┘
                │
                ▼
      Recherche des tags GHCR
                │
                ▼
       Sélection des versions
                │
                ▼
          Génération .env
                │
                ▼
        Pull des images Docker
                │
                ▼
      Tests avec Testcontainers
                │
                ▼
             GatewayIT
                │
          ┌─────┴─────┐
          │           │
         OK          KO
          │           │
          ▼           ▼
   Déploiement      Pipeline
    production       arrêté
```

Après validation des tests, Jenkins lance :

```bash
docker compose pull
docker compose up -d
```

Le `docker-compose.yml` utilisé à cette étape correspond au déploiement de production.

---

# 🧪 Testcontainers

Les tests utilisent :

```java
ComposeContainer
```

pour démarrer le fichier :

```text
../docker-compose.test.yml
```

Le test expose le service :

```text
gateway:8080
```

avec une stratégie d'attente :

```text
Wait.forListeningPort()
```

et un délai maximal de démarrage de trois minutes.

Testcontainers fournit ensuite dynamiquement le host et le port utilisés par le Gateway.

Les tests communiquent donc avec :

```text
http://<host>:<port>/api/v1
```

plutôt qu'avec un port fixe.

---

# 🔬 Tests d'intégration

Les tests sont réalisés au travers du Gateway afin de vérifier l'intégration des différents services.

## Authentification

Les tests vérifient notamment :

* authentification valide ;
* authentification avec mauvais mot de passe ;
* accès à une ressource protégée sans token.

## Patients

Les tests couvrent :

* création ;
* modification ;
* suppression ;
* données minimales ;
* validation des données.

## Notes

Les tests couvrent :

* ajout d'une note ;
* suppression des notes d'un patient.

## Risque diabète

Les tests vérifient les quatre niveaux de risque :

```text
NONE
BORDERLINE
IN_DANGER
EARLY_ONSET
```

Les scénarios créent les patients et leurs notes médicales avant d'interroger le service de calcul du risque.

---

# 🎬 Démonstration — `docker-compose.demo.yml`

Le fichier `docker-compose.demo.yml` a principalement pour objectif de fournir un **exemple complet de déploiement**.

Il permet notamment de montrer :

* l'ensemble des services nécessaires ;
* les images utilisées ;
* les versions attendues ;
* les variables d'environnement ;
* les paramètres nécessaires à la communication entre services ;
* les variables critiques nécessaires au fonctionnement de l'application.

Il constitue donc également une **référence de configuration** pour comprendre les paramètres nécessaires au déploiement de MediLabo.

Contrairement au fichier de production, les valeurs présentes dans ce fichier peuvent être directement visibles afin de faciliter la compréhension et la démonstration.

Les images sont récupérées depuis GHCR et utilisent :

```yaml
pull_policy: always
```

## Lancer la démonstration

```bash
docker compose -f docker-compose.demo.yml up -d
```

## Recréer les conteneurs

```bash
docker compose -f docker-compose.demo.yml up -d --force-recreate
```

## Arrêter

```bash
docker compose -f docker-compose.demo.yml down
```

## Accès

Frontend :

```text
http://localhost:9081
```

Gateway :

```text
http://localhost:9080
```

API :

```text
http://localhost:9080/api/v1
```

---

# 🖥️ Déploiement local — `docker-compose.local.yml`

Ce fichier permet de déployer MediLabo localement en **construisant les images directement à partir des projets sources**.

Les différents `build.context` utilisent l'organisation suivante :

```text
../cloudGateway
../front-medilabo
../infosPatients
../notesPatients
../risqueDiabetePatients
```

## Prérequis

Depuis le répertoire `deployment`, l'arborescence doit être :

```text
MediLabo/
├── cloudGateway/
├── deployment/
├── front-medilabo/
├── infosPatients/
├── notesPatients/
└── risqueDiabetePatients/
```

## Construire et démarrer

```bash
docker compose -f docker-compose.local.yml up -d --build
```

## Construire sans utiliser le cache

```bash
docker compose -f docker-compose.local.yml build --no-cache
docker compose -f docker-compose.local.yml up -d
```

## Recréer les conteneurs

```bash
docker compose -f docker-compose.local.yml up -d --build --force-recreate
```

## Arrêter

```bash
docker compose -f docker-compose.local.yml down
```

## Arrêter et supprimer les volumes

```bash
docker compose -f docker-compose.local.yml down -v
```

---

# 🧪 Tests d'intégration locaux — `docker-compose.test.local.yml`

Ce fichier permet de reproduire localement les tests d'intégration utilisés dans le pipeline Jenkins.

Les images des services sont construites depuis les projets locaux.

L'organisation attendue est la même que pour le déploiement local :

```text
MediLabo/
├── cloudGateway/
├── deployment/
├── front-medilabo/
├── infosPatients/
├── notesPatients/
└── risqueDiabetePatients/
```

Les tests peuvent ainsi être développés et validés localement avant leur exécution par Jenkins.

---

# 🌐 Ports

Les principaux ports exposés sont :

| Service  | Port interne | Port hôte |
| -------- | -----------: | --------: |
| Gateway  |       `8080` |    `9080` |
| Frontend |         `80` |    `9081` |

Les autres services restent accessibles via le réseau Docker `medilabo`.

---

# 🗄️ Persistance

Le déploiement de production utilise les volumes suivants :

```text
db_pg_info_patient_data
db_pg_diabete_risk_data
mongo_note_patient_data
```

Ils permettent de conserver les données des bases lors des redémarrages et recréations de conteneurs.

Pour afficher les volumes :

```bash
docker volume ls
```

Pour inspecter un volume :

```bash
docker volume inspect db_pg_info_patient_data
```

---

# 🧰 Commandes Docker Compose utiles

Les commandes suivantes sont utiles pour administrer l'environnement.

## Démarrer

```bash
docker compose up -d
```

## Démarrer et reconstruire

```bash
docker compose up -d --build
```

## Démarrer, reconstruire et recréer les conteneurs

```bash
docker compose up -d --build --force-recreate
```

## Recréer les conteneurs même si Docker considère qu'ils sont à jour

```bash
docker compose up -d --force-recreate
```

## Supprimer les conteneurs orphelins

```bash
docker compose up -d --remove-orphans
```

## Reconstruire sans cache

```bash
docker compose build --no-cache
```

## Télécharger les images

```bash
docker compose pull
```

## Arrêter les conteneurs

```bash
docker compose down
```

## Arrêter et supprimer les volumes

```bash
docker compose down -v
```

## Arrêter et supprimer les conteneurs orphelins

```bash
docker compose down --remove-orphans
```

## Voir l'état des services

```bash
docker compose ps
```

## Voir les logs

```bash
docker compose logs
```

## Suivre les logs en temps réel

```bash
docker compose logs -f
```

## Logs d'un seul service

```bash
docker compose logs -f gateway
```

Exemples :

```bash
docker compose logs -f infos-patient
docker compose logs -f notes-patient
docker compose logs -f risk-patient
docker compose logs -f gateway
docker compose logs -f front
```

## Redémarrer un service

```bash
docker compose restart gateway
```

## Voir la configuration finale

Cette commande est particulièrement utile pour vérifier les variables et les images réellement utilisées :

```bash
docker compose config
```

Pour afficher uniquement les images :

```bash
docker compose config | grep image:
```

---

# 🔄 Cycle complet

Le cycle de développement et de déploiement est le suivant :

```text id="g9g7cx"
             DÉVELOPPEMENT LOCAL
                     │
                     ▼
          docker-compose.local.yml
                     │
                     ▼
             Tests locaux
                     │
                     ▼
              Build Docker
                     │
                     ▼
             Publication GHCR
                     │
                     ▼
              ┌────────────┐
              │   Jenkins  │
              └──────┬─────┘
                     │
                     ▼
          Récupération des versions
                     │
                     ▼
             Génération .env
                     │
                     ▼
       docker-compose.test.yml
                     │
                     ▼
             Testcontainers
                     │
                     ▼
               GatewayIT
                     │
                ┌────┴────┐
                │         │
               OK        KO
                │         │
                ▼         ▼
          Déploiement   Arrêt du
          production    pipeline
                │
                ▼
        docker-compose.yml
```

---

# 📊 Synthèse

| Environnement | Fichier                         | Images      | Utilisation                       |
| ------------- | ------------------------------- | ----------- | --------------------------------- |
| Développement | `docker-compose.local.yml`      | Build local | Développement                     |
| Tests locaux  | `docker-compose.test.local.yml` | Build local | Tests d'intégration               |
| Démonstration | `docker-compose.demo.yml`       | GHCR        | Démo + référence de configuration |
| CI Jenkins    | `docker-compose.test.yml`       | GHCR        | Validation avant production       |
| Production    | `docker-compose.yml`            | GHCR        | Déploiement réel                  |

---

# 🧩 Technologies

* **Docker / Docker Compose**
* **PostgreSQL 16**
* **MongoDB 7**
* **GitHub Container Registry**
* **Jenkins**
* **Testcontainers**
* **JUnit 5**
* **RestAssured**
* **Spring Boot**
* **Spring Cloud Gateway**

---

# 🩺 MediLabo

Le répertoire `deployment` centralise les configurations nécessaires pour déployer et tester l'ensemble de l'écosystème MediLabo.

Les images Docker sont versionnées et publiées sur GHCR. Jenkins récupère automatiquement les versions disponibles, exécute les tests d'intégration avec Testcontainers et, lorsque les tests sont validés, lance le déploiement de production.

