# ep-db

## Requisitos

Sbt

## Compilar

No diretório `ep-project`:

```shell
cd ep-db
sbt
[ep-db] compile
```

## Configurações

Editar arquivo de configurações ``config.properties`` e adicionar argumentos para conexão com banco de dados:

```properties
db.host=
db.database=
db.port=
db.user=
db.password=
db.batch_size=100

grobid.home=grobid-home
grobid.properties=grobid-home/config/grobid.properties
```
## Configuração do banco de dados Postgres

* Criar novo banco de dados:
```sql
CREATE DATABASE <db.database>
```
..* <db.database>: mesmo valor encontrado no arquivo ``config.properties``.

* Criar tabelas, indices e triggers pelo shell execute:
```shell
psql -U <db.user> -W -f db/database-schema.sql <db.database>
```
..* <db.database>: mesmo valor encontrado no arquivo ``config.properties``.
..* fornece a senha para conexão com o banco de dados (<db.password>) para o usuário <db.user>

## Permissão de execução

Atribuindo permissão de execução aos scripts: 

```shell
chmod +x *.sh
```

## Executar extração de documentos

```shell
.scripts/docExtractor <direcroty_to_pdfs>
```

## Atualizar projeções multidimensionais

```shell
.scripts/updateMDP
```

## Atualizar ranking

```shell
.scripts/updatePageRank
```



