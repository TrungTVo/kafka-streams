Sample `ksql` scripts are created in `/src/main/resources/ksql/` folder and mounted to `/home/appuser/ksql_scripts/` inside `ksqldb-server` container.

To run a `ksql` file. In `ksqldb-server` container, run:
```
ksql> RUN SCRIPT '/home/appuser/ksql_scripts/<file_name>';
```

NOTE: this way is only runnable for `CREATE`, `DROP`, `INSERT`, etc. query but not `SELECT`