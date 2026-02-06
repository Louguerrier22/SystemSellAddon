package ca.l1cked.systemselladdon.storage

import ca.l1cked.systemselladdon.database.DatabaseSettingsLoader
import ca.l1cked.systemselladdon.database.DatabaseType
import ca.l1cked.systemselladdon.util.ItemStackSerializer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class MySQLSellStorage(private val config: FileConfiguration, private val dataFolderPath: String) : SellStorage {

    private lateinit var connection: Connection

    override fun init() {
        val settings = DatabaseSettingsLoader.load(config, dataFolderPath)
        if (settings.type != DatabaseType.MYSQL) {
            error("MySQLSellStorage initialized with non-mysql settings")
        }
        Class.forName(settings.driverClass())
        connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username, settings.password)
        createTables()
    }

    private fun createTables() {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    ts BIGINT NOT NULL,
                    personal_multiplier DOUBLE NOT NULL,
                    total DOUBLE NOT NULL
                ) ENGINE=InnoDB;
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transaction_categories (
                    tx_id BIGINT NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    amount DOUBLE NOT NULL,
                    PRIMARY KEY (tx_id, category),
                    FOREIGN KEY (tx_id) REFERENCES transactions(id) ON DELETE CASCADE
                ) ENGINE=InnoDB;
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transaction_items (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tx_id BIGINT NOT NULL,
                    item_blob MEDIUMTEXT NOT NULL,
                    amount INT NOT NULL,
                    FOREIGN KEY (tx_id) REFERENCES transactions(id) ON DELETE CASCADE
                ) ENGINE=InnoDB;
                """.trimIndent()
            )
        }

        createIndex("idx_transactions_uuid_ts", "transactions", "player_uuid, ts DESC")
        createIndex("idx_transactions_name_ts", "transactions", "player_name, ts DESC")
        createIndex("idx_items_tx", "transaction_items", "tx_id")
    }

    private fun createIndex(name: String, table: String, columns: String) {
        try {
            connection.createStatement().use { st ->
                st.executeUpdate("CREATE INDEX $name ON $table ($columns)")
            }
        } catch (_: SQLException) {
            // ignore duplicate index errors
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
        val sql = "SELECT DISTINCT player_name FROM transactions ORDER BY LOWER(player_name) ASC LIMIT ?"
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
