# Release Notes - Обновление от 15.01.2026

## Исправления

### 🔧 Критическое исправление: Отсутствующий дескриптор

**Проблема:**
```
java.lang.AssertionError: class io.jenkins.plugins.swarmcloud.SwarmComputerLauncher is missing its descriptor
```

**Решение:**
- Добавлен `SwarmComputerLauncher.DescriptorImpl` с аннотацией `@Extension`
- Плагин теперь корректно регистрируется в Jenkins
- Агенты успешно запускаются и подключаются

**Затронутые файлы:**
- [src/main/java/io/jenkins/plugins/swarmcloud/SwarmComputerLauncher.java](src/main/java/io/jenkins/plugins/swarmcloud/SwarmComputerLauncher.java)

## Улучшения

### 🎨 Поддержка темной темы Dashboard

**Что добавлено:**
- Автоматическое определение темы через `@media (prefers-color-scheme: dark)`
- Поддержка темной темы Jenkins через `[data-theme="dark"]`
- CSS переменные для всех цветовых схем
- Улучшенная контрастность всех UI элементов

**Преимущества:**
- ✅ Комфортная работа в темное время суток
- ✅ Снижение нагрузки на глаза
- ✅ Автоматическое переключение в зависимости от настроек
- ✅ Совместимость со всеми современными браузерами

**Цветовые схемы:**

Светлая тема → Темная тема:
- Фон: `#ffffff` → `hsl(222 47% 11%)`
- Карточки: `#ffffff` → `hsl(217 33% 17%)`
- Текст: `hsl(222 47% 11%)` → `hsl(210 40% 98%)`
- Границы: `hsl(214 32% 91%)` → `hsl(217 33% 25%)`

**Затронутые файлы:**
- [src/main/resources/io/jenkins/plugins/swarmcloud/SwarmDashboard/index.jelly](src/main/resources/io/jenkins/plugins/swarmcloud/SwarmDashboard/index.jelly)

## Установка

### Обновление плагина:

1. **Скачайте обновленный плагин:**
   - Файл: `target/swarm-agents-cloud.hpi`
   - Размер: 7.1 MB
   - SHA-256: `23C3839C6BE564302E17AE9484A71983D420E8DD82FD3BCEB967280159238A72`

2. **Установите через Jenkins UI:**
   - Перейдите в **Manage Jenkins** → **Plugins** → **Advanced settings**
   - В разделе **Deploy Plugin** загрузите файл `.hpi`
   - Нажмите **Deploy**

3. **Перезапустите Jenkins:**
   ```bash
   systemctl restart jenkins
   # или через UI: Manage Jenkins → Prepare for Shutdown → Restart
   ```

### Настройка SonarQube агента (если требуется):

Используйте конфигурацию из [sonar-agent-config.yaml](sonar-agent-config.yaml):

```yaml
templates:
  - name: "sonar-scanner"
    image: "stepa86/sonar-scanner-cli:latest"
    labelString: "sonar"
    connectionTimeoutSeconds: 600
    # ... остальные настройки
```

Подробнее: [SONAR_SETUP.md](SONAR_SETUP.md)

## Проверка

### 1. Проверьте работу плагина:
```bash
# Проверьте логи Jenkins
tail -f /var/log/jenkins/jenkins.log | grep swarmcloud
```

### 2. Проверьте Dashboard:
- Откройте: `http://jenkins:8080/swarm-dashboard/`
- Переключите тему Jenkins
- Dashboard должен автоматически адаптироваться

### 3. Запустите тестовый агент:
```groovy
pipeline {
    agent { label 'sonar' }
    stages {
        stage('Test') {
            steps {
                sh 'echo "Agent works!"'
            }
        }
    }
}
```

## Известные проблемы

Нет известных проблем в данном релизе.

## Совместимость

- Jenkins: 2.479.3+
- Java: 17+
- Docker Swarm: Любая версия с Swarm mode
- Браузеры: Chrome 76+, Firefox 67+, Safari 12.1+

## Документация

- [README.md](README.md) - Основная документация
- [SONAR_SETUP.md](SONAR_SETUP.md) - Настройка SonarQube агента
- [DARK_THEME_IMPROVEMENTS.md](DARK_THEME_IMPROVEMENTS.md) - Детали темной темы

## Поддержка

- Issues: [GitHub Issues](https://github.com/jenkinsci/swarm-agents-cloud-plugin/issues)
- Логи: **Manage Jenkins** → **System Log** → добавить логгер `io.jenkins.plugins.swarmcloud` (уровень `FINE`)
