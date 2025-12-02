# Настройка SonarQube агента для Jenkins Swarm

## Проблема
Получали ошибку:
```
java.lang.AssertionError: class io.jenkins.plugins.swarmcloud.SwarmComputerLauncher is missing its descriptor
```

## Решение

### 1. Установите исправленный плагин

Исправление уже добавлено в код - в класс `SwarmComputerLauncher` добавлен необходимый дескриптор.

Плагин собран: `target/swarm-agents-cloud.hpi`

Для установки:
1. Зайдите в Jenkins: **Manage Jenkins** → **Plugins** → **Advanced settings**
2. В разделе **Deploy Plugin** загрузите файл `target/swarm-agents-cloud.hpi`
3. Перезапустите Jenkins

### 2. Настройте SonarQube агент

Добавьте шаблон агента с меткой 'sonar' в конфигурацию Swarm Cloud.

#### Через UI

1. Зайдите в **Manage Jenkins** → **Clouds** → Выберите ваш Swarm Cloud
2. Добавьте новый шаблон:
   - **Name**: `sonar-scanner`
   - **Image**: `stepa86/sonar-scanner-cli:latest`
   - **Label String**: `sonar`
   - **Remote FS**: `/home/jenkins/agent`
   - **Number of Executors**: `1`
   - **Max Instances**: `3`
   - **CPU Limit**: `2.0`
   - **Memory Limit**: `4g`
   - **Connection Timeout**: `600` (10 минут)
   - **Idle Timeout**: `30` минут

3. Добавьте переменные окружения:
   - `SONAR_HOST_URL`: `http://sonarqube:9000` (замените на ваш SonarQube URL)
   - `JAVA_OPTS`: `-Xmx2g`

#### Через Configuration as Code (JCasC)

Используйте файл `sonar-agent-config.yaml`:

```yaml
jenkins:
  clouds:
    - swarmAgentsCloud:
        name: "docker-swarm"
        dockerHost: "tcp://swarm-manager:2376"
        jenkinsUrl: "http://jenkins:8080"
        swarmNetwork: "jenkins-network"
        maxConcurrentAgents: 10
        templates:
          - name: "sonar-scanner"
            image: "stepa86/sonar-scanner-cli:latest"
            labelString: "sonar"
            remoteFs: "/home/jenkins/agent"
            numExecutors: 1
            maxInstances: 3
            cpuLimit: "2.0"
            memoryLimit: "4g"
            cpuReservation: "0.5"
            memoryReservation: "1g"
            connectionTimeoutSeconds: 600
            idleTimeoutMinutes: 30
            environmentVariables:
              - key: "SONAR_HOST_URL"
                value: "http://sonarqube:9000"
              - key: "JAVA_OPTS"
                value: "-Xmx2g"
```

### 3. Использование в Pipeline

```groovy
pipeline {
    agent none
    stages {
        stage('SonarQube') {
            agent {
                label 'sonar'
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'sonar-scanner'
                }
            }
        }
    }
}
```

Или с использованием swarmAgent:

```groovy
pipeline {
    agent none
    stages {
        stage('SonarQube') {
            steps {
                swarmAgent(cloud: 'docker-swarm', template: 'sonar-scanner') {
                    withSonarQubeEnv('SonarQube') {
                        sh 'sonar-scanner'
                    }
                }
            }
        }
    }
}
```

## Проверка

После настройки:

1. Запустите тестовую задачу с меткой `sonar`
2. Jenkins должен автоматически создать Docker Swarm сервис
3. Агент подключится к Jenkins через WebSocket
4. Проверьте логи агента в Jenkins UI

## Диагностика

Если агент не запускается:

1. Проверьте Docker Swarm:
   ```bash
   docker service ls
   docker service logs <service-name>
   ```

2. Проверьте логи Jenkins: **Manage Jenkins** → **System Log** → Добавьте логгер для `io.jenkins.plugins.swarmcloud`

3. Проверьте сетевую связность между Jenkins и Docker Swarm

4. Убедитесь, что образ `stepa86/sonar-scanner-cli:latest` доступен и содержит Jenkins inbound-agent

## Увеличение timeout

Если SonarQube агенту нужно больше времени для подключения (например, если образ большой):

```yaml
connectionTimeoutSeconds: 900  # 15 минут
```

## Дополнительные настройки

### Кеширование SonarQube

Для ускорения повторных сканирований:

```yaml
mounts:
  - type: "volume"
    source: "sonar-cache"
    target: "/opt/sonar-scanner/.sonar/cache"
```

### Размещение на определенных нодах

```yaml
placementConstraints:
  - "node.labels.type==build"
```
