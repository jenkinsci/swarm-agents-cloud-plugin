# Swarm Agents Cloud Plugin для Jenkins

[![en](https://img.shields.io/badge/lang-en-blue.svg)](README.md)
[![ru](https://img.shields.io/badge/lang-ru-green.svg)](README.ru.md)

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/swarm-agents-cloud.svg)](https://plugins.jenkins.io/swarm-agents-cloud/)
[![Java 21+](https://img.shields.io/badge/java-21%2B-blue.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Современный плагин Jenkins для динамического создания агентов в кластерах Docker Swarm.

## Почему этот плагин?

Этот плагин — **современная замена** заброшенному [docker-swarm-plugin](https://github.com/jenkinsci/docker-swarm-plugin) (последнее обновление — февраль 2021, 54+ нерешённых issues).

### Сравнение с docker-swarm-plugin

| Функция | **swarm-agents-cloud** | docker-swarm-plugin |
|---------|------------------------|---------------------|
| **Последнее обновление** | 2025 (активная разработка) | 2021 (заброшен) |
| **Открытых Issues** | — | 54+ |
| **Версия Java** | 21+ | 8+ |
| **Версия Jenkins** | 2.528.3+ | Устаревшая |
| **WebSocket подключение** | ✅ | ❌ |
| **Поддержка JCasC** | ✅ Полная | ❌ |
| **REST API** | ✅ Полный CRUD | ❌ |
| **Prometheus метрики** | ✅ | ❌ |
| **Docker Secrets/Configs** | ✅ | ❌ |
| **Наследование шаблонов** | ✅ | ❌ |
| **Rate Limiting** | ✅ | ❌ |
| **Health Checks** | ✅ | ❌ |
| **Pipeline DSL** | ✅ `swarmAgent {}` | ❌ |
| **Аудит логирование** | ✅ | ❌ |
| **Очистка orphan-сервисов** | ✅ Автоматическая | ❌ |
| **Тёмная тема Dashboard** | ✅ | ❌ |
| **TLS поддержка** | ✅ Работает | ⚠️ Сломана |
| **XSS уязвимости** | ✅ Исправлены | ⚠️ SECURITY-2811 |

### Ключевые преимущества

- **Современный стек** — Java 21, WebSocket, актуальный Jenkins API
- **DevOps-Ready** — JCasC, REST API, Prometheus, Pipeline DSL
- **Безопасность** — TLS, Secrets, валидация ввода
- **Надёжность** — Rate Limiting, Retry с backoff, Health Checks, очистка orphan
- **Активная поддержка** — регулярные обновления vs заброшенный проект

## Возможности

- **Динамическое создание агентов** — автоматически создаёт Docker Swarm сервисы по требованию
- **WebSocket подключение** — современное соединение (не требуется открывать входящие TCP порты)
- **TLS/SSL аутентификация** — полная поддержка Docker TLS сертификатов
- **Configuration as Code** — полная совместимость с JCasC
- **Управление ресурсами** — CPU/memory лимиты и резервации для каждого шаблона
- **Health Checks** — настраиваемый мониторинг состояния контейнеров
- **Secrets & Configs** — интеграция с Docker Swarm secrets и configs
- **Rate Limiting** — встроенное ограничение скорости создания (защита от thundering herd)
- **Dashboard** — мониторинг кластера в реальном времени на `/swarm-dashboard`
- **REST API** — программное управление через `/swarm-api`
- **Наследование шаблонов** — наследование настроек от родительских шаблонов (`inheritFrom`)
- **Prometheus метрики** — эндпоинт `/swarm-api/prometheus`
- **Аудит логирование** — отслеживание всех событий создания агентов
- **Pipeline DSL** — нативный шаг `swarmAgent` для Jenkinsfile
- **Retry с Backoff** — автоматический exponential backoff при ошибках
- **Очистка orphan** — автоматическая очистка устаревших сервисов

## Требования

- Jenkins 2.528.3 или новее (см. [требования Jenkins к Java](https://www.jenkins.io/doc/book/platform-information/support-policy-java/))
- Docker Swarm кластер (`docker swarm init`)

## Быстрый старт

### Через UI

1. Перейдите в **Manage Jenkins** → **Clouds**
2. Нажмите **New cloud** → **Docker Swarm Agents Cloud**
3. Настройте:
   - **Docker Host**: `tcp://your-swarm-manager:2376`
   - **Credentials**: Docker Server Credentials (для TLS)
   - **Max Concurrent Agents**: лимит общего количества агентов

### Через Configuration as Code (JCasC)

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        dockerHost: "tcp://swarm-manager:2376"
        credentialsId: "docker-tls-creds"
        maxConcurrentAgents: 10
        jenkinsUrl: "http://jenkins:8080/"
        swarmNetwork: "jenkins-agents"
        templates:
          - name: "maven"
            image: "jenkins/inbound-agent:latest"
            labelString: "maven docker"
            remoteFs: "/home/jenkins/agent"
            numExecutors: 2
            maxInstances: 5
            cpuLimit: "2.0"
            memoryLimit: "4g"
            connectionTimeoutSeconds: 300
            idleTimeoutMinutes: 30
```

## Шаблоны агентов

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `name` | Идентификатор шаблона | Обязательно |
| `image` | Docker образ | `jenkins/inbound-agent:latest` |
| `labelString` | Jenkins метки (через пробел) | — |
| `remoteFs` | Рабочая директория агента | `/home/jenkins/agent` |
| `numExecutors` | Количество executors на агент | 1 |
| `maxInstances` | Максимум контейнеров из шаблона | 5 |
| `cpuLimit` | Лимит CPU (например, "2.0") | — |
| `memoryLimit` | Лимит памяти (например, "4g") | — |
| `cpuReservation` | Резервация CPU | — |
| `memoryReservation` | Резервация памяти | — |

### Расширенные опции

| Поле | Описание |
|------|----------|
| `privileged` | Запуск с повышенными привилегиями |
| `user` | Запуск от конкретного пользователя (например, "1000:1000") |
| `hostname` | Hostname контейнера |
| `command` | Кастомная команда entrypoint |
| `disableContainerArgs` | Не передавать аргументы в entrypoint |
| `capAddString` | Linux capabilities для добавления |
| `capDropString` | Linux capabilities для удаления |
| `dnsServersString` | Кастомные DNS серверы |

### Наследование шаблонов

```yaml
templates:
  - name: "base"
    image: "jenkins/inbound-agent:latest"
    cpuLimit: "2.0"
    memoryLimit: "4g"
    connectionTimeoutSeconds: 300

  - name: "maven"
    inheritFrom: "base"
    labelString: "maven docker"
    environmentVariables:
      - key: "MAVEN_OPTS"
        value: "-Xmx1g"
```

### Docker Secrets

```yaml
templates:
  - name: "with-secrets"
    secrets:
      - secretName: "my-secret"
        fileName: "secret.txt"
        targetPath: "/run/secrets"
```

### Аутентификация в Registry (Приватные образы)

Поддержка загрузки образов из приватных Docker registry при создании агентов.

**Поддерживаемые Registry:**
- Docker Hub (публичные и приватные репозитории)
- Google Container Registry (gcr.io)
- AWS Elastic Container Registry (ECR)
- GitHub Container Registry (ghcr.io)
- Azure Container Registry (azurecr.io)
- Любой приватный registry с аутентификацией username/password

**Шаги настройки:**

1. **Создайте Credentials в Jenkins:**
   - Перейдите в **Manage Jenkins** → **Credentials**
   - Добавьте **Username with password** credentials для вашего registry
   - Запомните ID credentials

2. **Настройте шаблон:**
   - В настройках шаблона выберите credentials из выпадающего списка **Registry Credentials**
   - Плагин автоматически определит registry из имени образа

**Пример Configuration as Code:**

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        templates:
          - name: "private-agent"
            image: "myregistry.com/jenkins-agent:latest"
            registryCredentialsId: "docker-registry-creds"
            labelString: "private docker"
```

**Настройка через UI:**
Перейдите в настройки шаблона → выпадающий список **Registry Credentials** → Выберите ваши credentials

**Наследование шаблонов:**
Registry credentials могут наследоваться от родительских шаблонов:

```yaml
templates:
  - name: "base-private"
    image: "myregistry.com/base:latest"
    registryCredentialsId: "docker-registry-creds"

  - name: "maven-private"
    inheritFrom: "base-private"
    image: "myregistry.com/maven:latest"
    # Наследует registryCredentialsId от base-private
```

### Extra Hosts (Кастомные записи /etc/hosts)

Добавление кастомных соответствий hostname-IP в файл `/etc/hosts` контейнера, эквивалент флага `--add-host` в Docker.

**Случаи использования:**
- Локальная разработка и тестирование
- Кастомное DNS-разрешение для внутренних сервисов
- Алиасы для баз данных и сервисов

**Формат:** `hostname:IP` (одна запись на строку, поддерживаются IPv4 и IPv6)

**Пример Configuration as Code:**

```yaml
templates:
  - name: "agent-with-hosts"
    image: "jenkins/inbound-agent:latest"
    extraHosts:
      - "database.local:10.0.0.50"
      - "internal-registry:192.168.1.100"
      - "api.internal:172.16.0.10"
```

**Настройка через UI:**
Перейдите в настройки шаблона → текстовое поле **Extra Hosts** → Введите пары `hostname:IP` (по одной на строку)

**Пример:**
```
myhost:192.168.1.1
database:10.0.0.5
registry.local:172.16.0.20
```

**Наследование шаблонов:**
Extra hosts объединяются при использовании наследования шаблонов:

```yaml
templates:
  - name: "base"
    extraHosts:
      - "shared-db:10.0.0.1"

  - name: "maven"
    inheritFrom: "base"
    extraHosts:
      - "maven-repo:192.168.1.50"
    # Контейнер будет иметь обе записи extra hosts
```

**Валидация:**
- Автоматическая проверка IP-адресов (IPv4 и IPv6)
- Проверка формата hostname
- Понятные сообщения об ошибках для некорректных записей

## Pipeline DSL

```groovy
pipeline {
    agent none
    stages {
        stage('Build') {
            steps {
                swarmAgent(cloud: 'docker-swarm', template: 'maven') {
                    sh 'mvn clean package'
                }
            }
        }
    }
}
```

## REST API

Базовый URL: `http://jenkins/swarm-api/`

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/clouds` | Список всех clouds |
| GET | `/cloud?name=X` | Детали облака |
| GET | `/templates?cloud=X` | Список шаблонов |
| GET | `/template?cloud=X&name=Y` | Детали шаблона |
| GET | `/agents?cloud=X` | Список агентов |
| GET | `/prometheus` | Prometheus метрики |
| GET | `/audit?cloud=X` | Аудит лог |
| POST | `/provision?cloud=X&template=Y` | Создать агента |
| PUT | `/template` | Обновить шаблон |

## Dashboard

Доступен по адресу `http://jenkins/swarm-dashboard/`

- Обзор состояния кластера
- Статус нод и ресурсы
- Список активных сервисов
- Поддержка тёмной темы

## Prometheus метрики

```text
http://jenkins/swarm-api/prometheus
```

Метрики: `swarm_agents_total`, `swarm_agents_active`, `swarm_nodes_total`, `swarm_memory_total_bytes` и др.

## Устранение неполадок

### Включение отладочного логирования

**Manage Jenkins** → **System Log** → Добавить логгер `io.jenkins.plugins.swarmcloud` с уровнем `FINE`

### Частые проблемы

| Ошибка | Решение |
|--------|---------|
| "Unsupported protocol scheme: https" | Используйте `tcp://` вместо `https://` |
| "Connection refused" | Проверьте, что Docker API открыт |
| "TLS handshake failed" | Настройте Docker Server Credentials |
| "This node is not a swarm manager" | Выполните `docker swarm init` |

## Сборка из исходников

```bash
git clone https://github.com/jenkinsci/swarm-agents-cloud-plugin.git
cd swarm-agents-cloud-plugin
mvn clean package -DskipTests
# Результат: target/swarm-agents-cloud.hpi
```

## Участие в разработке

Смотрите [CONTRIBUTING.md](CONTRIBUTING.md)

## Лицензия

MIT License — см. [LICENSE](LICENSE)
