 There is a dedicated task to migrate data from prior versions of CTIA. 
 This task will run through all configured stores, transform and copy data to new elasticsearch indices. 
 A migration state will be stored in a configured state to enable restart.

# migration steps
 - as the migration task copies indexes, make sure you have enough disk space before launching it.
 - keep current ctia ES properties: host, port, transport and current store indices.
 - modify alias and rollover conditions according to the need of future indices. Note that only `max_docs` rollover condition will be considered during migration.
 - if possible, stop any processes that push data into CTIA.
 - launch migration task while your CTIA instance keep running. You can launch parallel migrations for different stores.
 - stop CTIA.
 - complete migration using `--restart` parameter to complete migration and handle writes that occured during migration.
 - after the migration task completes, you will need to edit your properties, changing each store index to the new one.
 - launch new version of CTIA. 
 - this task doesn't alter the existing indices, you should delete them after a successful migration.
 
 
 In case of failure, you have 2 solutions depending on the situation:
   - you can relaunch the task at will, it should fully recreate the new indices.
   - you can restart this migration with `--restart` parameter, it will reuse the migration
 
 Make sure the resulting indices from your prefix configuration don't match existing ones as they will be deleted (unless the migration is restarted)
 
# launch the task with:
 
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


# available migrations

| migration task | target ctia versions          | sample command                                                                                          |
|----------------|-------------------------------|---------------------------------------------------------------------------------------------------------|
|         0.4.16 | all versions before 1.0.0-rc1 | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 0.4.16 -p 0.4.16 -b 200 -c |
|         0.4.28 | all versions before 1.1.0     | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 0.4.28 -p 0.4.28 -b 200 -c |
|          1.0.0 | 1.1.0                         | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m 1.0.0 -p 1.0.0 -b 200 -c |
|       identity | used for mapping migration    | java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores -m identity -p 1.0.1 -b 200 -c|
