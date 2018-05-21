import java.sql.ResultSet
import java.sql.Statement

Iterator iter = [
        [id: 100, age: 30, first: 'Zara', last: 'Ali'],
        [id: 101, age: 30, first: 'Mahnaz', last: 'Fatma'],
        [id: 102, age: 30, first: 'Zaid', last: 'Khan'],
        [id: 103, age: 28, first: 'Sumit', last: 'Mittal'],
].iterator()

def currentRs

ResultSet resultSet = [
        getInt   : { key ->
            return currentRs[key]
        },
        getString: { key -> currentRs[key] },
        next     : {
            if (iter.hasNext()) {
                currentRs = iter.next();
                return true
            }
            return false
        },
        close    : {}
] as ResultSet

def sendAndRetry = { method, sql ->
    vertx.eventBus().send('mock.event', "${method} ${sql}".toString())
}

createStatement = {
    [
            execute      : { sql ->
                sendAndRetry 'execute', sql
                return true
            },
            executeUpdate: { sql ->
                sendAndRetry 'executeUpdate', sql
                return 0
            },
            executeQuery : { sql ->
                sendAndRetry 'executeQuery', sql
                return resultSet
            },
            close        : {}
    ] as Statement
}

close = {}