# ep-project
Repositório para o código do projeto de Mestrado

## Requisitos

- sbt: [https://www.scala-sbt.org/](https://www.scala-sbt.org/)
- PostgreSQL: [https://www.postgresql.org/](https://www.postgresql.org/)
- GROBID: [https://github.com/kermitt2/grobid](https://github.com/kermitt2/grobid)


## Estrutura

- `ep-db`: sub-projeto para gerenciamento da base de dados (extração e processamento de documentos e acesso aos dados (CRUD)).
- `ep-project`: projeto principal (aplicação Web). 

## Compilando `ep-db` para extração de documentos

Ir para diretório `ep-db`:
```shell
cd ep-project/ep-db
```

Compilar e criar distribuição (dist/ep-db.jar):
```
sbt assembly
```

## Executando scripts para criação da base dados

Os scripts encontram-se no diretório `ep-project/ep-db/scripts`:
- **`docExtractor.sh`**: scripts para extração de documentos: 
  - ```./scripts/docExtractor.sh <diretorio_PDFs> <config_file>```
    - **`<diretorio_PDFs>`**: caminho completo para diretório onde encontram-se arquivos PDF's a serem extraídos.
    - **`<config_file>`**: arquivo de configuração alternativo (opcional, caso contrário utiliza arquivo padrão: `ep-db/src/main/resources/config.properties`).
- **`updateMDP.sh`**: atualiza projeção multimensional dos documentos na base de dados (atualiza x,y):
  - ```./scripts/updateMDP.sh <config_file>```
- **`updatePageRank.sh`**: atualiza PageRank (ranqueamento):
  - ```./scripts/updatePageRank.sh <config_file>```
- **`updateQuadTree.sh`**: atualiza Quad Tree:
  - ```./scripts/updateQuadTree.sh <config_file>```
  
## Compilando aplicação (`ep-project`)

No diretório raiz (`ep-project`) executar:
```shell
sbt dist 
```
Irá gerar o arquivo `target/universal/ep-project-{$version}.zip`.

## Configuração

### Configuração da aplicação Web (`ep-project`)

#### Configuração da conexão com base de dados

Principais configurações da aplicação (`ep-project/conf/application.conf`). Editar valores para dos campos de conexão com o banco de dados (PostgreSQL): `default-url`, `default.jdbcUrl`, `default.username` e `default.password`.
```
db {
  # You can declare as many datasources as you want.
  # By convention, the default datasource is named `default`

  default.driver = org.postgresql.Driver
  default.driverClassName = org.postgresql.Driver
  default.url = "jdbc:postgresql://localhost/db_name"
  default.jdbcUrl = "jdbc:postgresql://localhost/db_name"
  default.username = "db_username"
  default.password = "db_password"
  ...
}
```
#### Arquivo de configuração do `ep-db`

Para que as configurações do `ep-db` possam ser carregadas pela aplicação é necessário fornecer o caminho completo para arquivo de configuração do `ep-db`. Caso não especificado ou não encontrado o arquivo padrão será utilizado (`ep-db/src/main/resources/config.properties`).

```# ep-db configuration file
ep_db {
#       config_file = ep-db/src/main/resources/config.properties
}
```

Altere o valor do campo `config_file` para o caminho completo do arquivo com configuração alternativa à padrão e remova o comentário `#` do ínicio da linha.


**Sempre que alterar o arquivo padrão de configuração `ep-db/src/main/resources/config.properties` deve-se recompilar o código todo**





  
  





