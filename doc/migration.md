 There is a dedicated task to migrate data from prior versions of CTIA. 
 This task will run through all configured stores, transform and copy data to new elasticsearch indices. 
 A migration state will be stored in a configured state to enable restart.

# :warning: Prepare Migration :warning:
 - As the migration task copies indexes, make sure you have enough disk space before launching it.
 - Make sure the resulting indices from your prefix configuration don't match existing ones as they will be deleted.
 - Prepare the migration properties (ex: `ctia_migration.properties`):
   - Keep current CTIA ES properties: host, port, transport and current store indices. These properties will be used to read source indices.
   - Modify `aliased`, `rollover`, and `shards` options according to the need of future indices. The migrated indices will be built with these options. Note that only `max_docs` rollover condition will be considered during migration.
   - Configure desired number of shards.
 - Prepare the future migration properties (ex: `ctia_vX.X.X.properties`) that will be used to restart CTIA on migration indices. In particular you will have to set `aliased`, `rollover`, and `indexname` options according to targeted indices state.
 - If possible, stop any processes that push data into CTIA.


# Migration Steps
 - replace `ctia.properties` by the migration property file that you prepared (`ctia_migration.properties`).
 - Launch migration task while your CTIA instance keep running. You can launch parallel migrations for different stores.
 - Stop CTIA server instance.
 - Complete migration using `--restart` parameter to handle writes that occurred during the migration. Keep the same migration properties.
 - After the migration task completes, replace CTIA properties of the server instance with the one you prepared to launch the server with migrated indices (ex: `ctia_vX.X.X.properties`).
 - Launch new version of CTIA. 
 - The migration task doesn't alter the existing indices, you can delete them after a successful migration.

 
 In case of failure, you have 2 solutions depending on the situation:
   - you can relaunch the task at will, it should fully recreate the new indices.
   - you can restart this migration with `--restart` parameter, it will reuse the migration
 
 
# Launch the task with:
 
`java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores <options>`

or from source

`lein run -m ctia.task.migration.migrate-es-stores <options>`

## task arguments
| argument                    | description                                      | example      | default value      |
|-----------------------------|--------------------------------------------------|--------------|--------------------|
| -i, --id id                 | id of the migration state to create or restart   | migration-1  | generated          |
| -p, --prefix prefix         | prefix of the newly created indices              | 1.1.0        | none, required     |
| -m, --migrations migrations | a comma separated list of migration ids to apply | 0.4.28,1.0.0 | none, required     |
| -b, --batch-size size       | number of migrated documents per batch           | 1000         | 100                |
| , --buffer-size size        | max batches in buffer between source and target  | 10           | 30                 |
| -s, --stores stores         | comma separated list of stores to migrate        | tool,malware | all                |
| -c, --confirm               | really do the migration?                         |              | false (positional) |
| -r, --restart               | restart ongoing migration?                       |              | false (positional) |
| -h, --help                  | prints usage                                     |              |                    |

## examples

- apply migration 0.4.28 and 1.0.0, use prefix 1.1.0 for newly created indices, only for stores tool and malware, with batches of size 1000, and assign migration-1 as id for the migration state:
    `lein run -m ctia.task.migration.migrate-es-stores -m 0.4.28,1.0.0 -p 1.1.0 -s tool,malware -b 1000 -i migration-1 -c`
- the previous command completed, you restart your ctia instance on new indices, but you want to handle writes between the end of your migration and your restart of ctia. restart previous migration with`--restart` or '-r':
    `lein run -m ctia.task.migration.migrate-es-stores -m 0.4.28,1.0.0 -p 1.1.0 -s tool,malware -b 1000 -i migration-1 -c --restart`
- the migration failed, you corrected the issue that made it fail, you can restart it with `--restart` (or `-r`) parameter (same command as above). 


# Available migrations

| migration task | target ctia versions          | sample command                                                                                          |
|----------------|-------------------------------|---------------------------------------------------------------------------------------------------------|
|         0.4.16 | all versions before 1.0.0-rc1 | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 0.4.16 -p 0.4.16 -b 200 -c |
|         0.4.28 | all versions before 1.1.0     | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 0.4.28 -p 0.4.28 -b 200 -c |
|          1.0.0 | 1.1.0                         | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 1.0.0 -p 1.0.0 -b 200 -c |
|       identity | used for mapping migration    | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m identity -p 1.0.1 -b 200 -c|
