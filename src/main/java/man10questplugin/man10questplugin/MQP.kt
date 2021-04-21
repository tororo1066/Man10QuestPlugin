package man10questplugin.man10questplugin

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MQP : JavaPlugin(),Listener {

    private lateinit var es : ExecutorService
    private val prefix = "§f[§d§lMan10§a§lQuest§f]"

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this,this)
        getCommand("mq")?.setExecutor(this)
        es = Executors.newCachedThreadPool()
        val mysql = MySQLManager(this,"mqp")
        mysql.execute("CREATE TABLE IF NOT EXISTS `mqp` (\n" +
                "  `owner` varchar(36) DEFAULT NULL,\n" +
                "  `cont` varchar(36) DEFAULT NULL,\n" +
                "  `item` text,\n" +
                "  `amount` int DEFAULT NULL\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player)return true
        if (args.isEmpty()) {
            val inv = Bukkit.createInventory(null,54, Component.text("$prefix 依頼一覧 page 1"))
            es.execute {
                val mysql = MySQLManager(this,"mqpQuestFinder")
                val rs = mysql.query("SELECT * FROM mqp;")
                var int = 0
                while (rs?.next() == true){
                    if (int == 54)break
                    val item = itemFromBase64(rs.getString("item"))!!
                    val meta = item.itemMeta
                    meta.lore(mutableListOf(Component.text("§b§l必要数:${rs.getInt("amount")}").asComponent(),Component.text("§a§l締切日:${rs.getInt("month")}/${rs.getInt("day")}").asComponent(),Component.text("§6§l報酬:${rs.getInt("price")}").asComponent()))
                    item.itemMeta = meta
                    inv.setItem(int,item)
                    int++
                }
                rs?.close()
                mysql.close()
                return@execute
            }
            sender.openInventory(inv)

            return true
        }
        when(args[0]){

            "add"->{
                if (args.size != 4)return true
                if (args[1].toIntOrNull() == null || args[2].toIntOrNull() == null || args[3].toIntOrNull() == null){
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~6400) (報酬10000~)")
                    return true
                }
                if (args[1].toInt() !in 1..12){
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~6400) (報酬10000~)")
                    return true
                }
                if (args[2].toInt() !in 64..6400){
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~6400) (報酬10000~)")
                    return true
                }
                if (args[3].toInt() < 10000){
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~6400) (報酬10000~)")
                    return true
                }
                if (sender.inventory.itemInMainHand.type == Material.AIR){
                    sender.sendmsg("手にアイテムを持ってください！")
                    return true
                }
                sender.sendmsg("依頼を出しています...")
                es.execute {
                    val mysql = MySQLManager(this,"mqqQuestAdd")
                    if (mysql.execute("INSERT INTO mqp (owner, item, amount, price, month, day) VALUES ('${sender.uniqueId}', '${itemToBase64(sender.inventory.itemInMainHand)}', ${args[2].toInt()}, ${args[3].toInt()}, ${ZonedDateTime.now().monthValue+args[1].toInt()}, ${ZonedDateTime.now().dayOfMonth});")){
                        sender.sendmsg("依頼を出すことに成功しました！")
                    }else{
                        sender.sendmsg("§4依頼を出すことに失敗しました")
                    }
                    mysql.close()
                    return@execute
                }
            }
        }
        return true
    }

    @EventHandler
    fun click(e : InventoryClickEvent){
        if (!e.view.title().toString().contains(prefix))return
        val p = e.whoClicked as Player
        if (e.view.title().toString().contains("依頼一覧")){
            e.isCancelled = true
            val page = e.view.title().toString().split(" ")[3].replace(",","").replace("\"","").toInt()
            when(e.hotbarButton){
                0 ->{
                    if (page == 1)return
                    var inv : Inventory? = null
                    es.execute {
                        inv = pageopen(p,page-1)
                        return@execute
                    }
                    inv?.let { p.openInventory(it) }
                }
                1 ->{
                    var inv : Inventory? = null
                    es.execute {
                        if (MySQLManager(this,"mqpPageChecker").count("mqp") < page * 53)return@execute
                        inv = pageopen(p,page+1)
                        return@execute
                    }
                    inv?.let { p.openInventory(it) }
                }
            }
        }
    }


    private fun pageopen(p : Player, i : Int): Inventory {
        val inv = Bukkit.createInventory(null,54, Component.text("$prefix 依頼一覧 page $i"))
        val mysql = MySQLManager(this,"mqpQuestFinder")
        val rs = mysql.query("SELECT * FROM mqp;")
        server.logger.info("unko!")
        var int = (i-1) * 53
        while (rs?.next() == true){
            if (UUID.fromString(rs.getString("owner")) == p.uniqueId)continue
            if (int == i * 53)break
            val item = itemFromBase64(rs.getString("item"))!!
            val meta = item.itemMeta
            meta.lore(mutableListOf(Component.text("§b§l必要数:${rs.getInt("amount")}").asComponent(),Component.text("§a§l締切日:${rs.getInt("month")}/${rs.getInt("day")}").asComponent(),Component.text("§6§l報酬:${rs.getInt("price")}").asComponent()))
            item.itemMeta = meta
            inv.setItem(int,item)
            int++
        }
        rs?.close()
        mysql.close()
        return inv
    }

    ///////////////////////////////
    //base 64
    //////////////////////////////
    private fun itemFromBase64(data: String): ItemStack? = try {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        val dataInput = BukkitObjectInputStream(inputStream)
        val items = arrayOfNulls<ItemStack>(dataInput.readInt())

        // Read the serialized inventory
        for (i in items.indices) {
            items[i] = dataInput.readObject() as ItemStack
        }

        dataInput.close()
        items[0]
    } catch (e: Exception) {
        null
    }

    @Throws(IllegalStateException::class)
    fun itemToBase64(item: ItemStack): String {
        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            val items = arrayOfNulls<ItemStack>(1)
            items[0] = item
            dataOutput.writeInt(items.size)

            for (i in items.indices) {
                dataOutput.writeObject(items[i])
            }

            dataOutput.close()

            return Base64Coder.encodeLines(outputStream.toByteArray())

        } catch (e: Exception) {
            throw IllegalStateException("Unable to save item stacks.", e)
        }
    }

    private fun Player.sendmsg(message : String){
        this.sendMessage(prefix + message)
    }
}