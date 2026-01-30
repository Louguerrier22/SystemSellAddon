package ca.l1cked.dsellsystem.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.bukkit.inventory.ItemStack
import java.util.UUID
import ca.l1cked.dsellsystem.storage.SellItemEntry
import ca.l1cked.dsellsystem.util.ItemStackSerializer

class SQLiteSellStorage(private val dataFolder: File) : SellStorage {

    private lateinit var connection: Connection

    override fun init() {
        dataFolder.mkdirs()
        val dbFile = File(dataFolder, "dsellsystem.db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    ts INTEGER NOT NULL,
                    personal_multiplier REAL NOT NULL,
                    total REAL NOT NULL
                );
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transaction_categories (
                    tx_id INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    amount REAL NOT NULL,
                    PRIMARY KEY (tx_id, category),
                    FOREIGN KEY (tx_id) REFERENCES transactions(id) ON DELETE CASCADE
                );
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_uuid_ts ON transactions(player_uuid, ts DESC);")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_name_ts ON transactions(player_name, ts DESC);")
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transaction_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    tx_id INTEGER NOT NULL,
                    item_blob TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    FOREIGN KEY (tx_id) REFERENCES transactions(id) ON DELETE CASCADE
                );
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_items_tx ON transaction_items(tx_id);")
        }
    }

    override fun close() {
        if (::connection.isInitialized) {
            connection.close()
        }
    }

    @Synchronized
    override fun recordSell(
        playerUuid: UUID,
        playerName: String,
        timestampMillis: Long,
        personalMultiplier: Double,
        paidByCategory: Map<String, Double>,
        totalPaid: Double,
    ): Long {
        connection.autoCommit = false
        try {
            val txId = connection.prepareStatement(
                "INSERT INTO transactions(player_uuid, player_name, ts, personal_multiplier, total) VALUES (?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.setString(2, playerName)
                ps.setLong(3, timestampMillis)
                ps.setDouble(4, personalMultiplier)
                ps.setDouble(5, totalPaid)
                ps.executeUpdate()

                ps.generatedKeys.use { keys ->
                    if (!keys.next()) error("Failed to get generated transaction id")
                    keys.getLong(1)
                }
            }

            connection.prepareStatement(
                "INSERT INTO transaction_categories(tx_id, category, amount) VALUES (?, ?, ?)"
            ).use { ps ->
                for ((category, amount) in paidByCategory) {
                    ps.setLong(1, txId)
                    ps.setString(2, category)
                    ps.setDouble(3, amount)
                    ps.addBatch()
                }
                ps.executeBatch()
            }

            connection.commit()
            return txId
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    
    @Synchronized
    override fun recordSellItems(txId: Long, items: List<ItemStack>) {
        if (txId < 0 || items.isEmpty()) return
        val grouped = linkedMapOf<String, Int>()
        val shulkerSingles = mutableListOf<String>()
        for (item in items) {
            if (item.amount <= 0) continue
            val normalized = item.clone()
            normalized.amount = 1
            val blob = ItemStackSerializer.encode(normalized)
            if (normalized.type.name.contains("SHULKER_BOX")) {
                repeat(item.amount) { shulkerSingles.add(blob) }
                continue
            }
            grouped[blob] = (grouped[blob] ?: 0) + item.amount
        }
        if (grouped.isEmpty() && shulkerSingles.isEmpty()) return
        connection.prepareStatement(
            "INSERT INTO transaction_items(tx_id, item_blob, amount) VALUES (?, ?, ?)"
        ).use { ps ->
            for ((blob, amount) in grouped) {
                ps.setLong(1, txId)
                ps.setString(2, blob)
                ps.setInt(3, amount)
                ps.addBatch()
            }
            for (blob in shulkerSingles) {
                ps.setLong(1, txId)
                ps.setString(2, blob)
                ps.setInt(3, 1)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    @Synchronized
    override fun getItemHistory(uuid: UUID, limit: Int, offset: Int): List<SellItemEntry> {
        val sql = """
            SELECT i.id as item_id, i.tx_id, i.item_blob, i.amount, t.player_uuid, t.ts
            FROM transaction_items i
            JOIN transactions t ON t.id = i.tx_id
            WHERE t.player_uuid = ?
            ORDER BY t.ts DESC, i.id DESC
            LIMIT ? OFFSET ?;
        """.trimIndent()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.setInt(2, limit)
            ps.setInt(3, offset)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<SellItemEntry>()
                while (rs.next()) {
                    val blob = rs.getString("item_blob") ?: continue
                    val item = ItemStackSerializer.decode(blob) ?: continue
                    val amount = rs.getInt("amount")
                    item.amount = amount
                    out.add(
                        SellItemEntry(
                            id = rs.getLong("item_id"),
                            transactionId = rs.getLong("tx_id"),
                            playerUuid = UUID.fromString(rs.getString("player_uuid")),
                            timestampMillis = rs.getLong("ts"),
                            item = item,
                        )
                    )
                }
                return out
            }
        }
    }

    @Synchronized
    override fun getItemHistoryCount(uuid: UUID): Int {
        val sql = "SELECT COUNT(*) as c FROM transaction_items i JOIN transactions t ON t.id = i.tx_id WHERE t.player_uuid = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs ->
                if (!rs.next()) return 0
                return rs.getInt("c")
            }
        }
    }

    @Synchronized
    override fun purgeOlderThan(timestampMillis: Long) {
        val sql = "DELETE FROM transactions WHERE ts < ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setLong(1, timestampMillis)
            ps.executeUpdate()
        }
    }

@Synchronized
    override fun getHistory(uuid: UUID, limit: Int): List<SellTransaction> {
        // Subquery prevents LIMIT from being applied to joined rows instead of transactions.
        val sql =
            """
            SELECT t.id, t.player_uuid, t.player_name, t.ts, t.personal_multiplier, t.total,
                   c.category, c.amount
            FROM (
                SELECT * FROM transactions
                WHERE player_uuid = ?
                ORDER BY ts DESC, id DESC
                LIMIT ?
            ) t
            LEFT JOIN transaction_categories c ON c.tx_id = t.id
            ORDER BY t.ts DESC, t.id DESC;
            """.trimIndent()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                return unpackTransactions(rs)
            }
        }
    }

    @Synchronized
    override fun resolveUuidByLastKnownName(name: String): UUID? {
        val sql =
            "SELECT player_uuid FROM transactions WHERE lower(player_name) = lower(?) ORDER BY ts DESC, id DESC LIMIT 1"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return UUID.fromString(rs.getString("player_uuid"))
            }
        }
    }

    @Synchronized
    override fun knownNames(limit: Int): List<String> {
        val sql = "SELECT DISTINCT player_name FROM transactions ORDER BY player_name COLLATE NOCASE ASC LIMIT ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) {
                    out.add(rs.getString("player_name"))
                }
                return out
            }
        }
    }

    private fun unpackTransactions(rs: java.sql.ResultSet): List<SellTransaction> {
        val out = mutableListOf<SellTransaction>()

        data class MutableTx(
            val id: Long,
            val playerUuid: UUID,
            val playerName: String,
            val ts: Long,
            val personalMultiplier: Double,
            val total: Double,
            val categories: MutableMap<String, Double>,
        )

        val byId = linkedMapOf<Long, MutableTx>()
        while (rs.next()) {
            val id = rs.getLong("id")
            val tx = byId.getOrPut(id) {
                MutableTx(
                    id = id,
                    playerUuid = UUID.fromString(rs.getString("player_uuid")),
                    playerName = rs.getString("player_name"),
                    ts = rs.getLong("ts"),
                    personalMultiplier = rs.getDouble("personal_multiplier"),
                    total = rs.getDouble("total"),
                    categories = linkedMapOf(),
                )
            }

            val category = rs.getString("category")
            if (category != null) {
                tx.categories[category] = rs.getDouble("amount")
            }
        }

        for (tx in byId.values) {
            out.add(
                SellTransaction(
                    id = tx.id,
                    playerUuid = tx.playerUuid,
                    playerName = tx.playerName,
                    timestampMillis = tx.ts,
                    personalMultiplier = tx.personalMultiplier,
                    totalPaid = tx.total,
                    paidByCategory = tx.categories.toMap(),
                )
            )
        }

        return out
    }
}
