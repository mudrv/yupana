# Yupana

[![Join the chat at https://gitter.im/rusexpertiza-llc/yupana](https://badges.gitter.im/rusexpertiza-llc/beanpuree.svg)](https://gitter.im/rusexpertiza-llc/yupana?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/rusexpertiza-llc/yupana.svg?branch=master)](https://travis-ci.org/rusexpertiza-llc/yupana)
[![codecov](https://codecov.io/gh/rusexpertiza-llc/yupana/branch/master/graph/badge.svg)](https://codecov.io/gh/rusexpertiza-llc/yupana)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.yupana/yupana-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.yupana/yupana-core_2.12)

Yupana is an open source analytic platform designed for big data analysis.

It provides:

 - перевод транзакционной информации в форму пригодную для бизнес анализа;
 - хранение обработанной  информации в формате оптимизированном для выполнения аналитических задач на многомерных
   временных рядах;
 - массовую и Online обработку данных.

The platform supports SQL-like queries which can be run both in standalone server or in Apache Spark cluster.

В состав Yupana также входит набор примеров использования, который может быть использован как стартовая точка для
реализации аналитической платформы для решения реальных задач.

You can get more information (currently only in Russian) at the site www.yupana.org.

## Table of contents

 - [Общие сведения о Yupana](#yupana)
 - [Начало работы](#start)
   - [Системные требования](#requirements)
   - [Сборка проекта](#build)
   - [Запуск](#examples)
     - [Server](#examples-server)
     - [ETL](#examples-etl)
     - [QueryRunner](#examples-query-runner)
   - [Адаптация Yupana к существующему окружению](#adaptation)
   - [Yupana SQL](#sql)
     - [Правила наименования полей](#sql-fields)
     - [Литералы](#sql-literals)
     - [Примеры запросов](#sql-examples)
     - [Функции](#sql-functions)
 - [Структура проекта](#structure)
   - [yupana-api](#structure-api)
   - [yupana-core](#structure-core)
   - [yupana-hbase](#structure-hbase)
   - [yupana-proto](#structure-proto)
   - [yupana-jdbc](#structure-jdbc)
   - [yupana-akka](#structure-akka)
   - [yupana-spark](#structure-spark)
   - [yupana-schema](#structure-schema)
   - [yupana-external-links](#structure-links)
   - [yupana-examples](#structure-examples)

## Общие сведения о Yupana <a href="#yupana"></a>

Yupana architecture does not coupled with the low level storage.  Different DAO implementations can be provided.
Currently there is only one DAO implementation based on Apache HBase.  The data is saved in time series.

Time series is a colleciton of obseving parameters values, measured at different time moments.

Time series structure:

 - Время измерения -- обязательная размерность временного ряда, является частью первичного составного ключа. При
   выполнении запросов всегда должны быть указаны ограничения по времени;
 - Измерения -- поля сущности, которые являются частью первичного составного ключа и позволяют выполнять быстрый поиск.
   Например: идентификатор устройства или название товара;
 - Метрики -- значения наблюдений. Например: сумма и количество;
 - Внешние связи -- интерфейсы отображения и/или группировки размерностей, которые позволяют определить древовидные
   размерности временного ряда. Например: Город отображается в уникальный идентификатор устройства.

## Начало работы <a href="#start"></a>

### Системные требования <a href="#requirements"></a>

1. JDK 8;
2. GNU/Linux (работа на других окружениях не проверялась);
3. Apache HBase 1.3.x с поддержкой сжатия Snappy;
4. Apache Spark 2.4.x для запуска запросов на кластере.  Кроме того, в прилагаемых примерах загрузка данных также производится
   из Spark-приложения, хотя это и не является обязательным условием;
5. Кластер Apache Ignite 2.7.0 при использовании распределенных кэшей в Ignite (опционально);
6. sbt -- для сборки проекта.

### Сборка проекта <a href="#build"></a>

Сборка проекта осуществляется с помощью sbt.  Некоторые команды в sbt shell:

 - compile -- компиляция проекта
 - test -- запуск юнит-тестов
 - assembly -- сборка толстых jar-ов, применяется в yupana-jdbc и yupana-examples

### Запуск <a href="#examples"></a>

Модуль `yupana-examples` содержит пример использования Yupana для анализа транзакций, которые могут быть использованы в
качестве основы при реализации собственных аналитических систем.
В примере реализована схема данных основанная на схеме из пакета yupana-schema с добавлением двух внешних связей (каталог
адресов и каталог организаций).  Каталог адресов (AddressCatalog) использует внутреннюю логику для отображения идентификатора
кассы на город.  Каталог организаций (OrganisationCatalog) отображает кассы на информацию об организации: тип организации
(например аптека, супермаркет) и обезличенный идентификатор. Каталог использует данные из внешнего источника -- базы данных PostgreSQL.

Для запуска примеров необходима база данных PostgreSQL.  По умолчанию используется база данных `yupana-example` на `localhost`.
Базу необходимо создать до запуска примера и миграции:

```
CREATE DATABASE yupana_example;
CREATE USER yupana WITH ENCRYPTED PASSWORD 'yupana';
GRANT CONNECT ON DATABASE yupana_example TO yupana;
```

После создания базы можно мигрировать:

```
sbt examples/flywayMigrate
```

Для запуска миграций с альтернативным адресом сервера PostgreSQL можно использовать команду:

```
sbt -Dflyway.url=jdbc:postgresql://server:port/db_name -Dflyway.user=db_user examples/flywayMigrate
```

#### 1. Server <a href="#examples-server"></a>

Реализация сервера на базе yupana-akka.

Запуск из sbt:

```
examples/runMain org.yupana.examples.server.Main
```

Настройки приложения в файле `yupana-examples/src/main/resources/application.conf`.  По умолчанию сервер слушает порт
`10101`.

Для подключения к серверу нужен JDBC драйвер.  Его можно собрать командой `sbt jdbc/assembly`.  Пакет с драйвером будет
сохранен в файл: `yupana/yupana-jdbc/target/scala-2.12/yupana-jdbc-assembly-{версия_проекта}-SNAPSHOT.jar`.
Для соединения с сервером с использованием Yupana JDBC нужно указать следующие параметры: 

  - URL: `jdbc:yupana://localhost:10101`
  - Class name (класс драйвера): `org.yupana.jdbc.YupanaDriver`

#### 2. ETL <a href="#examples-etl"></a>

Приложение эмулирует добавление данных Yupana.  Данные генерируются случайным образом.

Для запуска есть скрипт `deploy_etl.sh`. Подразумевается что Apache Spark установлен в `/opt/spark` или задана переменная
окружения `SPARK_HOME`. Перед запуском скрипта необходимо собрать толстый JAR (в sbt `examples/assembly`).

#### 3. QueryRunner <a href="#examples-query-runner"></a>

Приложение для запуска запросов к Yupana на кластере Apache Spark.  Результаты сохраняются в виде CSV файла.

SQL запрос для запуска и путь для сохранения результатов задается в `query-runner-app.conf`.

Запуск осуществляется скриптом `deploy_query_runner.sh`

### Адаптация Yupana к существующему окружению <a href="#adaptation"></a>

Модуль `yupana-examples` может быть использован в качестве основы для создания собственной аналитической системы.  Для этого потребуется:

1. Определить и реализовать внешние связи для доступа к существующим источникам данных.
2. Определить схему данных на основе существующей схемы.
3. Приведенная в примерах реализация сервера запросов является минимально полной, достаточно использовать схему реализованную
   на шаге 2 схему в сервере.  Однако для интеграции сервера в существующую инфраструктуру скорее всего понадобятся некоторые
   изменения (например чтение настроек из другого источника, использование дополнительных настроек для внешних источников и др).
4. Реализовать ETL процесс для наполнения базы данными.  Для периодического наполнения можно использовать Spark RDD, а для
   потокового DStream.

### Yupana SQL <a href="#sql"></a>

Для выполнения запросов Yupana поддерживает собственный диалект SQL.  Поддерживаются следующие операции:

 - `SELECT` -- выборка данных.
 - `UPSERT` -- вставка данных.
 - `SHOW TABLES` -- вывод списка таблиц.
 - `SHOW COLUMNS FROM <table_name>` -- вывод списка полей таблицы.
 - `SHOW QUERIES` -- просмотр истории запросов.
 - `KILL QUERY` -- остановка запроса.

#### Правила наименования полей <a href="#sql-fields"></a>

1. Время для любой схемы указывается как поле `time` типа TIMESTAMP. Доступны следующие функции для работы со временем:
`trunc_second`, `trunc_minute`, `trunc_hour`, `trunc_day`, `trunc_month`, `trunc_year`.
`extract_second`, `extract_minute`, `extract_hour`, `extract_day`, `extract_month`, `extract_year`.

При работе с драйвером следует учитывать, что выражение WHERE **обязательно и должно содержать**
временной интервал `time >= x and time < y`.

2. Поля таблицы указываются:
  - как есть (quantity или "quantity")
  - с указанием таблицы ("kkm_items"."quantity" или kkm_items.quantity)

3. Размерности указываются как есть (например kkmId)

4. Поля внешних связей указываются в виде `имясвязи_имяполя` (например ItemsInvertedIndex_phrase).

5. Фильтровать можно по размерностям, метрикам и полям внешних связей, времени используя =, !=, IN, IS NULL/NOT NULL для
строк и =, !=, >, >=, <, <=, IN, IS NULL/NOT NULL для остальных типов.


#### Литералы <a href="#sql-literals"></a>

Поддерживаются литералы следующих типов:

1. Строки: `'Hello!'`
2. Числа (целые либо с плавающей запятой): `42` или `1234.567`
3. Даты:
  - `TIMESTAMP '2018-08-06'`
  - `TIMESTAMP '2018-08-06 16:24:50'`
  - `TIMESTAMP '2018-08-06 16:24:50.123'`
  - `{ ts '2017-06-13' }`
  - `{ ts '2017-06-13 09:15:44' }`
  - `{ ts '2017-06-13 09:15:44.666' }`
4. Интервалы:
  - `INTERVAL '06:00:00'` -- 6 часов
  - `INTERVAL '1 12:00:00'` -- 1 день и 12 часов
  - `INTERVAL '1' HOUR` -- 1 час
  - `INTERVAL '30' MINUTE` -- 30 минут
  - `INTERVAL '2 12' DAY TO HOUR` -- 2 дня 12 часов
  - `INTERVAL '6 12:30' DAY TO MINUTE` -- 6 дней 12 часов 30 минут
  - `INTERVAL '3' MONTH` -- 3 месяца
  - `INTERVAL '1' YEAR` -- 1 год
  - `INTERVAL '3-10' MONTH TO DAY` -- 3 месяца и 10 дней

И т.д.  Важно понимать, что интервалы содержащие месяца и/или годы не могут быть использованы при сравнении длительности
интервала между двумя датами.  Это обуславливается тем что длина месяца или года зависит от определенной даты.

При выполнении математических операций (плюс или минус) над интервалами можно использовать любые интервалы.

#### Примеры запросов <a href="#sql-examples"></a>

Суммы продаж для указанной кассы за указанный период с разбивкой по дням:
```SQL
SELECT sum(sum), day(time) as d, kkmId
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01' AND kkmId = '10'
  GROUP BY d, kkmId
```

Суммы продаж товаров в которых встречается слово "штангенциркуль" за указанный период с разбивкой по дням:
```SQL
SELECT sum(sum), day(time) as d, kkmId
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01' AND itemsInvertedIndex_phrase = 'штангенциркуль'
  GROUP BY d, kkmId
```

Первой и последней продажи селедки за сутки:

```SQL
SELECT min(time) as mint, max(time) as maxt, day(time) as d
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01' and itemsInvertedIndex_phrase = 'селедка'
  GROUP BY d
```

Считаем количество продаж товаров, купленных в количестве больше 10:

```SQL
SELECT item, sum(CASE
    WHEN quantity > 9 THEN 1
    ELSE 0 )
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01'
  GROUP BY item
```

Применяем фильтры после расчета оконной функции:

```SQL
SELECT
  kkmId,
  time AS t,
  lag(time) AS l
FROM receipt
WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01'
GROUP BY kkmId
HAVING
  ((l - t) > INTERVAL '2' HOUR AND extract_hour(t) >= 8 AND extract_hour(t) <= 18) OR
  ((l - t) > INTERVAL '4' HOUR AND extract_hour(t) > 18 OR extract_hour(t) < 8)
```

Выбираем предыдущие три месяца:
```SQL
SELECT sum(sum), day(time) as d, kkmId
  FROM items_kkm
  WHERE time >= trunc_month(now() - INTERVAL '3' MONTH) AND time < trunc_month(now())
  GROUP BY d, kkmId
```

Агрегация по выражению:
```SQL
SELECT kkmId,
    (CASE WHEN totalReceiptCardSum > 0 THEN 1 ELSE 0) as paymentType
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01'
  GROUP BY paymentType, kkmId
```

Используем арифметику (`+`, `-`, `*`, `/`):
```SQL
SELECT sum(totalSum) as ts, sum(cardSum) * max(cashSum) / 2 as something
  FROM receipt
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01' AND kkmId = '11'
  GROUP BY kkmId
```

Группируем колбасу по вкусу и считаем сумму:
```SQL
SELECT
    item,
    case
      when contains_any(tokens(item), tokens('вареная')) then 'вареная'
      when contains_any(tokens(item), tokens('соленая')) then 'соленая'
      else 'невкусная' as taste,
    sum(sum)
  FROM items_kkm
  WHERE time >= TIMESTAMP '2019-06-01' AND time < TIMESTAMP '2019-07-01' AND itemsInvertedIndex_phrase = 'колбаса'
  GROUP BY item, taste
```

#### Функции <a name="sql-functions"></a>

| Функция        | Тип функции   | Типы аргументов         | Тип значения    | Описание                                                                          |
|----------------|:-------------:|:-----------------------:|:---------------:|-----------------------------------------------------------------------------------|
| min            | агрегация     | число, строка, время    | тот же          | Минимальное значение. Для строковых значение в лексикографическом порядке         |
| max            | агрегация     | число, строка, время    | тот же          | Максимальное значение. Для строковых значение в лексикографическом порядке        |
| sum            | агрегация     | число                   | тот же          | Сумма                                                                             |
| count          | агрегация     | любой                   | число           | Количество                                                                        |
| distinct_count | агрегация     | любой                   | число           | Количество уникальных значений                                                    |
| lag            | оконная       | любой                   | тот же          | Предыдущее значение в группе записей.  Группа определяется в запросе в секции группировки.  Сортировка по времени. |
| trunc_year     | унарная       | время                   | время           | Округление времени до года                                                        |
| trunc_month    | унарная       | время                   | время           | Округление времени до месяца                                                      |
| trunc_day      | унарная       | время                   | время           | Округление времени до дня                                                         |
| trunc_hour     | унарная       | время                   | время           | Округление времени до часа                                                        |
| trunc_minute   | унарная       | время                   | время           | Округление времени до минуты                                                      |
| trunc_second   | унарная       | время                   | время           | Округление времени до секунды                                                     |
| exract_year    | унарная       | время                   | число           | Извлечение значения года из времени                                               |
| exract_month   | унарная       | время                   | число           | Извлечение значения месяца из времени                                             |
| exract_day     | унарная       | время                   | число           | Извлечение значения дня из времени                                                |
| exract_hour    | унарная       | время                   | число           | Извлечение значения часа из времени                                               |
| exract_minute  | унарная       | время                   | число           | Извлечение значения минуты из времени                                             |
| exract_second  | унарная       | время                   | число           | Извлечение значения секунды из времени                                            |
| abs            | унарная       | число                   | число           | Значение числа по модулю                                                          |
| tokens         | унарная       | строка                  | массив строк    | Получение стемированых транслитерированых строк из строки                         |
| tokens         | унарная       | массив строк            | массив строк    | Получение стемированых транслитерированых строк из массива строк                  |
| split          | унарная       | строка                  | массив строк    | Разбиение строки на слова по пробелам                                             |
| length         | унарная       | строки, массивы         | строки, массивы | Длина строки или количество элементов в массиве                                   |
| array_to_string| унарная       | массив                  | строка          | Преобразование массивы в строку в формате "( a, b, .., n)"                        |
| +              | инфиксная     | число, строка, интервал | тот же          | Сложение                                                                          |
| -              | инфиксная     | число                   | тот же          | Вычитание                                                                         |
| *              | инфиксная     | число                   | тот же          | Умножение                                                                         |
| /              | инфиксная     | число                   | тот же          | Деление                                                                           |
| +              | инфиксная     | время и интервал        | время           | Сложение                                                                          |
| -              | инфиксная     | время и интервал        | время           | Вычитание                                                                         |
| -              | инфиксная     | время и время           | интервал        | Вычитание                                                                         |
| =              | инфиксная     | число, строка, время    | логический      | Сравнение на равенство                                                            |
| <> или !=      | инфиксная     | число, строка, время    | логический      | Сравнение на неравенство                                                          |
| >              | инфиксная     | число, строка, время    | логический      | Сравнение на больше                                                               |
| <              | инфиксная     | число, строка, время    | логический      | Сравнение на меньше                                                               |
| >=             | инфиксная     | число, строка, время    | логический      | Сравнение на больше или равно                                                     |
| <=             | инфиксная     | число, строка, время    | логический      | Сравнение на меньше или равно                                                     |
| contains       | бинарная      | массив и тип элемента   | логический      | True если массив содержит элемент, иначе False                                    |
| contains_all   | бинарная      | массив и массив         | логический      | True если массив1 содержит все элементы массива2, иначе False                     |
| contains_any   | бинарная      | массив и массив         | логический      | True если массив1 содержит хотя бы один элемент из массива2, иначе False          |
| contains_same  | бинарная      | массив и массив         | логический      | True если массив1 содержит те же элементы что и массив2 (в любом порядке)         |

Типы функций

- Агрегация -- функция вычисляющая общее значение из множества значений (например сумму или максимум).  Агрегации не могут использоваться вместе с оконными функциями.
- Оконная -- функция вычисляющая общее значение из множества значении и их порядка. Оконные функции не могут использоваться вместе с агрегациями. Не поддерживаются в реализации TSDB для Spark.
- Унарная -- функция над одним значением (например length или tokens).
- Инфиксная -- функция над двумя значениями, в SQL записывается между аргументами (например + или -).
- Бинарная -- функция с двумя значениями, например contains_all.

Кроме того, поддерживаются следующие SQL выражения:

| Выражение                 | Описание                                                                      |
|---------------------------|-------------------------------------------------------------------------------|
| `x IN (1, 2 .. z)`        | Проверка что `x` является одним из элементов заданного множества констант     |
| `x NOT IN (3, 4, .. z)`   | Проверка что `x` не является одним из элементов заданного множества констант  |
| `x IN NULL`               | Проверка что значение `x` не определено                                       |
| `x IS NOT NULL`           | Проверка что значение `x` определено                                          |

#### Добавление данных

Для добавления используется команда `UPSERT`.  При этом необходимо заполнить данные всех размерностей, время и необходимые
измерения.

```SQL
UPSERT INTO kkm_items(kkmId, item, operation_type, position, time, sum, quantity)
   VALUES ('12345', 'Пряник тульский', '1', '1', TIMESTAMP '2020-01-10 16:02:30', 100, 1)
```

Можно добавлять одновременно несколько значений:

```SQL
UPSERT INTO kkm_items(kkmId, item, operation_type, position, time, sum, quantity) VALUES
   ('12345', 'Пряник тульский', '1', '1', TIMESTAMP '2020-01-10 16:02:30', 300, 5),
   ('12345', 'Чай индийский', '1', '1', TIMESTAMP '2020-01-10 16:02:30', 100, 1)
```

## Структура проекта <a href="#structure"></a>

### yupana-api <a href="#structure-api"></a>

Этот модуль содержит определения базовых примитивов Yupana таких как типы данных и операции над ними, таблица, схема,
свертка, внешняя связь и др.

### yupana-core <a href="#structure-core"></a>

Реализация ядра хранилища.  В этом модуле содержится реализация работы с временными рядами, вне зависимости от типа используемого
хранилища.

### yupana-hbase <a href="#structure-hbase"></a>

Реализация хранилища поверх HBase.

### yupana-proto <a href="#structure-proto"></a>

Протокол взаимодействия между JDBC драйвером и сервером.

### yupana-jdbc <a href="#structure-jdbc"></a>

JDBC драйвер для Yupana.

### yupana-akka <a href="#structure-akka"></a>

Базовые части для реализации сервера поверх Akka. Принимает запросы через TCP.

### yupana-spark <a href="#structure-spark"></a>

Реализация TSDB работающая поверх HBase внутри Apache Spark.

### yupana-schema <a href="#structure-schema"></a>

Минимальное определение схемы для выполнения аналитики ОФД.

### yupana-external-links <a href="#structure-links"></a>

Реализация внешних связей, таких как инвертированный индекс, поиск сопутствующих товаров и связи на базе SQL таблиц.

### yupana-examples <a href="#structure-examples"></a>

Пример использования Yupana.  Содержит типичный набор приложений для работы с Yupana: сервер обработки запросов, ETL для
загрузки данных и приложения для запуска тяжелых вычислений на Spark.
