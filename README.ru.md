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
| **Последнее обновление** | 2026 (активная разработка) | 2021 (заброшен) |
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
| **UI: светлая/тёмная тема** | ✅ | ❌ |
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
- **Сворачиваемые группы шаблонов** — UI конфигурации облака остаётся удобным даже при больших списках шаблонов
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
| `oneShot` | Терминировать агент после одной завершённой сборки (требует `numExecutors=1`) |
| `user` | Запуск от конкретного пользователя (например, "1000:1000") |
| `hostname` | Hostname контейнера |
| `entrypoint` | Кастомный entrypoint контейнера |
| `disableContainerArgs` | Не передавать аргументы в entrypoint |
| `capAdd` / `capAddString` | Linux capabilities для добавления (требуется Docker Engine 20.10+ / API 1.41+) |
| `capDrop` / `capDropString` | Linux capabilities для удаления (требуется Docker Engine 20.10+ / API 1.41+) |
| `dnsServersString` | Кастомные DNS серверы |

### Подключение, таймауты и retry

Эти поля шаблона управляют тем, как долго плагин ждёт подключения агента и как
повторяет provisioning. Все опциональны и при отсутствии берутся дефолты ниже.

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `connectionTimeoutSeconds` | Максимальное время ожидания подключения агента перед отказом | 300 |
| `idleTimeoutMinutes` | Время простоя до терминирования агента (кроме one-shot) | 30 |
| `provisionRetryCount` | Количество повторов provisioning при ошибках | 3 |
| `provisionRetryDelayMs` | Начальная задержка между повторами в мс (exponential backoff) | 1000 |

```yaml
templates:
  - name: "build"
    image: "jenkins/inbound-agent:latest"
    connectionTimeoutSeconds: 300
    idleTimeoutMinutes: 30
    provisionRetryCount: 3
    provisionRetryDelayMs: 1000
```

### Health Checks

Подключение Docker health check к контейнеру агента для мониторинга его состояния
во время работы. Health check включается только когда задан `healthCheckCommand` —
плагин оборачивает команду как `CMD-SHELL`, поэтому достаточно указать shell-команду.

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `healthCheckCommand` | Shell-команда, выполняемая внутри контейнера для проверки здоровья. Пусто = без health check | — |
| `healthCheckIntervalSeconds` | Интервал между проверками | 30 |
| `healthCheckTimeoutSeconds` | Время ожидания одной проверки, после которого она считается проваленной | 10 |
| `healthCheckRetries` | Количество подряд идущих неудач до пометки контейнера unhealthy | 3 |

```yaml
templates:
  - name: "with-healthcheck"
    image: "jenkins/inbound-agent:latest"
    healthCheckCommand: "curl -f http://localhost:8080/ || exit 1"
    healthCheckIntervalSeconds: 30
    healthCheckTimeoutSeconds: 10
    healthCheckRetries: 3
```

Все эти поля объединяются через наследование шаблонов, как и остальные.

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
      - name: "MAVEN_OPTS"
        value: "-Xmx1g"
```

Наследование разрешается на **нескольких уровнях**, поэтому можно строить иерархии
(`base` → промежуточный → лист), и каждый шаблон объявляет только то, что добавляет:

```yaml
templates:
  - name: "base"                 # общие дефолты для всего
    image: "jenkins/inbound-agent:latest"
    cpuLimit: "2.0"

  - name: "jvm"                  # добавляет JVM-специфичные настройки
    inheritFrom: "base"
    environmentVariables:
      - name: "JAVA_OPTS"
        value: "-XX:MaxRAMPercentage=75"

  - name: "maven"               # лист: наследует всю цепочку base → jvm
    inheritFrom: "jvm"
    labelString: "maven"
```

Правила разрешения:

- Значения потомков приоритетнее значений предков; списки (переменные окружения,
  монтирования, capabilities и т.д.) объединяются по всей цепочке.
- Циклические ссылки (`a` → `b` → `a`) обнаруживаются и останавливаются с warning'ом
  вместо бесконечной рекурсии.
- Матчинг по меткам использует **разрешённый** (resolved) шаблон. Шаблон без
  эффективного `labelString` — ни собственного, ни унаследованного, как `base`,
  который держат только ради наследования, — матчит только job'ы без меток; он
  **не** является catch-all и не создаёт агентов для job'ов, требующих конкретную
  метку. Метки, объявленные на предке, наследуются, поэтому лист матчится и по ним.

### Эфемерные / One-Shot агенты

Установите `oneShot: true` на шаблоне, чтобы агент самоликвидировался сразу после одной
завершённой сборки, вместо ожидания idle-timeout. Полезно когда каждой сборке требуется
гарантированно чистая файловая система и состояние процессов (security-sensitive job'ы,
workload'ы, оставляющие временный мусор и т.п.).

```yaml
templates:
  - name: "ephemeral-build"
    image: "jenkins/inbound-agent:latest"
    labelString: "ephemeral"
    numExecutors: 1            # Должно быть 1 при oneShot=true
    oneShot: true
    idleTimeoutMinutes: 1      # Запасной путь для редкого случая "агент подключился, но build не запустился"
```

**Trade-off'ы:**

- Каждая сборка платит полную стоимость провижионинга контейнера (image pull + agent
  connection handshake), добавляя десятки секунд к queue time.
- `numExecutors` должен быть `1`. Форма отклоняет `oneShot=true` в сочетании с
  `numExecutors > 1`, потому что one-shot retention терминирует агент после первой
  завершённой сборки — это оборвёт любую другую сборку, выполняющуюся параллельно
  на втором executor'е.
- Если родительский шаблон (через `inheritFrom`) имеет `oneShot: true`, дочерние
  шаблоны наследуют этот флаг и не могут его отключить (тот же OR-merge, что и для
  `privileged`).

### Container Capabilities

`capAdd` / `capDrop` пробрасывают Linux capabilities в `TaskSpec.ContainerSpec` агентского
контейнера. Требуется Docker Engine 20.10 (API 1.41) или новее — старые версии Engine
молча игнорируют эти поля.

```yaml
templates:
  - name: "net-admin-agent"
    image: "jenkins/inbound-agent:latest"
    capAdd:
      - "CAP_NET_ADMIN"
    capDrop:
      - "CAP_CHOWN"
```

Newline-separated строковая форма (`capAddString`) тоже принимается — для совместимости
с существующими конфигурациями docker-swarm-plugin.

### Docker Secrets

```yaml
templates:
  - name: "with-secrets"
    secrets:
      - secretName: "my-secret"
        fileName: "secret.txt"
        targetPath: "/run/secrets"
```

### Docker Configs

Docker Swarm **configs** работают как secrets, но предназначены для нечувствительных
конфигурационных данных. Обязательно только `configName`; остальные поля опциональны.

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `configName` | Имя существующего Docker Swarm config | Обязательно |
| `targetPath` | Путь монтирования внутри контейнера | `/{configName}` |
| `fileName` | Имя файла в целевом местоположении | Вычисляется из `targetPath` |
| `fileMode` | Права на файл в octal (например `0644`) | — |
| `uid` / `gid` | UID / GID владельца файла внутри контейнера | — |

```yaml
templates:
  - name: "with-configs"
    configs:
      - configName: "app-config"
        targetPath: "/etc/app/config.ini"
        fileName: "config.ini"
        fileMode: "0644"
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

### Node Mode (режим использования)

`mode` управляет тем, какие job'ы принимают агенты шаблона, аналогично режимам
использования (usage mode) Jenkins-нод.

| Значение | Поведение |
|----------|-----------|
| `NORMAL` (по умолчанию) | Использовать шаблон максимально активно; также обслуживает job'ы без меток |
| `EXCLUSIVE` | Выполнять только job'ы, чей label expression совпадает с метками шаблона |

```yaml
templates:
  - name: "gpu"
    labelString: "gpu"
    mode: EXCLUSIVE        # зарезервирован исключительно для job'ов, запрашивающих "gpu"
```

### Переменные окружения

Инъекция переменных окружения в контейнер агента. `name` и `value` обязательны;
список объединяется через наследование шаблонов.

```yaml
templates:
  - name: "maven"
    environmentVariables:   # алиас: envVars
      - name: "MAVEN_OPTS"
        value: "-Xmx2g -XX:+UseG1GC"
      - name: "JAVA_HOME"
        value: "/opt/java/openjdk"
```

### Тома и монтирования

Монтирование путей хоста, именованных volume'ов или tmpfs в контейнер агента.

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `type` | `BIND` (путь на хосте), `VOLUME` (именованный volume) или `TMPFS` | Обязательно |
| `source` | Путь на хосте или имя volume | Обязательно |
| `target` | Путь монтирования внутри контейнера | Обязательно |
| `readOnly` | Смонтировать источник только для чтения | `false` |

```yaml
templates:
  - name: "maven"
    mounts:                 # алиас: hostBinds
      - type: BIND
        source: "/var/cache/maven"
        target: "/root/.m2/repository"
        readOnly: false
      - type: VOLUME
        source: "jenkins-workspace"
        target: "/workspace"
```

### Директории кеша сборки

`cacheDirs` — список путей внутри контейнера (каждый должен начинаться с `/`),
содержимое которых сохраняется между сборками для более быстрой инкрементальной
работы. Записи объединяются через наследование шаблонов.

```yaml
templates:
  - name: "maven"
    cacheDirs:
      - "/root/.m2/repository"
      - "/tmp/build-cache"
```

### Публикация портов

Публикация портов контейнера в ingress-сети Swarm. Формат `[hostPort:]containerPort[/protocol]`;
пустой host-порт означает случайный, протокол по умолчанию `tcp`.

```yaml
templates:
  - name: "with-ports"
    portBindings:           # алиас: portBinds (newline-separated строковая форма)
      - "8080:8080"         # host 8080 -> container 8080
      - ":5900"             # случайный host-порт -> container 5900
      - "443:8443/tcp"      # явный протокол
```

### Placement constraints и сетевые алиасы

`placementConstraints` привязывают задачи агента к конкретным нодам Swarm (синтаксис
Docker `--constraint`); `networkAliases` добавляют дополнительные DNS-алиасы в сети
Swarm. Оба — списки и объединяются через наследование шаблонов.

```yaml
templates:
  - name: "build"
    placementConstraints:
      - "node.role==worker"
      - "node.labels.type==build"
    networkAliases:
      - "build-agent"
```

### Настройка DNS

`dnsServers` задаёт кастомные резолверы, `dnsSearch` добавляет search-домены,
`dnsOptions` передаёт опции резолвера. Все — списки, объединяются через наследование.

```yaml
templates:
  - name: "build"
    dnsServers:             # алиасы: dnsServersString / dnsIps (через запятую)
      - "10.0.0.2"
    dnsSearch:
      - "internal.example.com"
    dnsOptions:
      - "ndots:2"
```

### Параметры ядра (sysctls)

Установка kernel parameters на контейнере (Docker `--sysctl`). Записи вида `key=value`,
объединяются через наследование шаблонов.

```yaml
templates:
  - name: "tuned"
    sysctls:                # алиас: sysctlsString (newline-separated)
      - "net.core.somaxconn=1024"
      - "net.ipv4.tcp_syncookies=0"
```

### Поведение при остановке контейнера

Управление тем, как контейнер останавливается при удалении агента.

| Поле | Описание | По умолчанию |
|------|----------|--------------|
| `stopSignal` | Сигнал, отправляемый для остановки контейнера (например `SIGTERM`, `SIGINT`) | Docker default (`SIGTERM`) |
| `stopGracePeriod` | Сколько секунд ждать до принудительного завершения контейнера | 10 |

```yaml
templates:
  - name: "graceful"
    stopSignal: "SIGTERM"
    stopGracePeriod: 30
```

### Профили безопасности

Применение seccomp и AppArmor профилей к контейнеру агента (Docker Engine 29+).

| Поле | Допустимые значения |
|------|---------------------|
| `seccompProfile` | `default`, `unconfined` или путь к кастомному профилю |
| `apparmorProfile` | `runtime/default`, `unconfined` или имя кастомного профиля |

```yaml
templates:
  - name: "hardened"
    seccompProfile: "default"
    apparmorProfile: "runtime/default"
```

### Generic Resources (GPU)

Запрос generic resources Swarm, таких как GPU. Каждая запись имеет вид `Kind=value`;
ноды должны объявлять соответствующий ресурс. Записи объединяются через наследование.

```yaml
templates:
  - name: "ml"
    labelString: "gpu"
    genericResources:       # алиас: genericResourcesString ("NVIDIA-GPU=1, FPGA=2")
      - kind: "NVIDIA-GPU"
        value: 1
```

## Pipeline DSL

### Declarative pipeline (`agent { swarmAgent { ... } }`)

Директива `swarmAgent` зарегистрирована в Pipeline Syntax → Declarative
Directive Generator → `agent`, поэтому плагин работает как drop-in замена
классического `docker-swarm-plugin`:

```groovy
pipeline {
    agent {
        swarmAgent {
            cloud 'docker-swarm'
            template 'maven'
            label 'maven java'
        }
    }
    stages {
        stage('Build') {
            steps { sh 'mvn clean package' }
        }
    }
}
```

Если настроен ровно один Docker Swarm-облако, поле `cloud` можно опустить.
Если не задана `label`, используется `labelString` шаблона. Поддерживается
inline-конфигурация без шаблона:

```groovy
pipeline {
    agent {
        swarmAgent {
            image 'jenkins/inbound-agent:alpine'
            label 'docker'
            cpuLimit '2.0'
            memoryLimit '4g'
        }
    }
    stages { ... }
}
```

### Scripted pipeline (`swarmAgent { ... }` шаг)

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

## Требования к Docker-образу / кастомные образы

При provisioning'е inbound-агента плагин передаёт реквизиты подключения к Jenkins
**двумя способами одновременно** — большинство образов работают «из коробки»:

- **Переменные окружения** (выставляются всегда):
  - `JENKINS_URL`, `JENKINS_AGENT_NAME`, `JENKINS_SECRET`, `JENKINS_AGENT_WORKDIR`
  - `JENKINS_WEB_SOCKET=true` (WebSocket inbound)
  - Алиасы для совместимости: `JNLP_URL`, `JENKINS_JNLP_URL`,
    `DOCKER_SWARM_PLUGIN_JENKINS_AGENT_JNLP_URL`, `DOCKER_SWARM_PLUGIN_JENKINS_AGENT_SECRET`
- **Аргументы командной строки** (позиционные): `[<JENKINS_URL>, <SECRET>, <AGENT_NAME>]`,
  добавляются к `ENTRYPOINT` образа.

### Рекомендуется: официальный inbound-agent

```yaml
templates:
  - name: default
    image: "jenkins/inbound-agent:alpine"   # или :latest, :jdk21, ...
    labelString: "docker"
```

У `jenkins/inbound-agent` уже есть `ENTRYPOINT ["/usr/local/bin/jenkins-agent"]`,
который корректно принимает позиционные аргументы — никакой дополнительной настройки не нужно.

### Кастомные образы без Jenkins-совместимого ENTRYPOINT

Если у образа **нет `ENTRYPOINT`** (или он не понимает соглашение
`url secret name`), позиционные аргументы становятся полной командой контейнера. Docker
попытается выполнить URL как бинарь и упадёт:

```text
OCI runtime create failed: ... exec: "https://<jenkins>/": no such file or directory
```

Два эквивалентных решения — выбирайте подходящее:

**Вариант A — задать ENTRYPOINT в шаблоне** (поле `entrypoint:` принимает свободную команду,
позиционные args при этом не передаются):

```yaml
templates:
  - name: custom
    image: "my-registry/my-agent:1.2.3"
    entrypoint: "/usr/local/bin/my-startup.sh"
```

**Вариант B — использовать только env vars** (образ сам читает `$JENKINS_URL`,
`$JENKINS_SECRET`, `$JENKINS_AGENT_NAME`):

```yaml
templates:
  - name: custom
    image: "my-registry/my-agent:1.2.3"
    disableContainerArgs: true   # отключить позиционные [url, secret, name]
```

`disableContainerArgs` — самый безопасный дефолт для slim или нестандартных образов.

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
- Список активных сервисов с указанием Swarm-ноды, на которой работает каждый агент
- Поддержка светлой и тёмной темы

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
| `OCI runtime create failed: ... exec: "https://...": no such file or directory` | У образа нет `ENTRYPOINT`. Используйте `jenkins/inbound-agent`, задайте `entrypoint:` в шаблоне или укажите `disableContainerArgs: true`. См. [Требования к Docker-образу](#требования-к-docker-образу--кастомные-образы). В лог сборки плагин также пишет строку `HINT:` с подсказкой при этой ошибке. |
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
