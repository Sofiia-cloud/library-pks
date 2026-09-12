# Библиотечная информационная система (КР 1)

## Требования
- Java 17+
- Maven 3.9+
- PostgreSQL 17

## Запуск
1. Создать БД: `createdb -U postgres library_db`
2. Залить схему: `psql -U postgres -d library_db -f schema.sql`
3. Скопировать `db.properties.example` → `db.properties`, вписать свой пароль
4. Собрать: `mvn clean compile`
5. Запустить: `mvn exec:java "-Dexec.mainClass=ru.mirea.library.Main"`

## Структура
...

ERL-диаграмма:
![alt text](mermaid.png)