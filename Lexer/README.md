# Зависимости
Перед использованием приложения может понадобиться запуск скрипта `script.sh`, доставляющего требуемые зависимости.

# Сборка
Для сборки необходимо склонировать репозиторий и запустить в папке проекта скрипт `gradle build`. 

# Запуск приложения
Запуск осуществляется командой `java -jar build/libs/Lexer.jar [PATH]`, принимающей путь к файлу с программой. Полученные лексемы или сообщение об ошибке будут выведены в консоль.

# Тестирование
Тесты запускаются командной `gradle test`.
