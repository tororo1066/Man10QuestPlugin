package man10questplugin.man10questplugin

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class MQPData(val owner: Player, val item: ItemStack, val amount: Int, val price: Int, val datetime : Date, val boolean: Int)

class MQP : JavaPlugin(),Listener {
    private lateinit var es : ExecutorService
    private val prefix = "§f[§d§lMan10§a§lQuest§f]§r"
    private val datamap = ConcurrentHashMap<Int,MQPData>()
    private lateinit var vault : VaultManager
    private var tax = 0.00
    private var enable = true

    private val sdf = SimpleDateFormat("yyyy-MM-dd")


    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this,this)
        getCommand("mq")?.setExecutor(this)
        vault = VaultManager(this)
        es = Executors.newCachedThreadPool()
        enable = config.getBoolean("mode")
        tax = config.getDouble("tax")
        val mysql = MySQLManager(this,"mqp")
        mysql.execute("CREATE TABLE IF NOT EXISTS`mqp` (\n" +
                "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
                "\t`owner` VARCHAR(36) NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`item` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`amount` INT NULL,\n" +
                "\t`price` INT NULL,\n" +
                "\t`date` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`boolean` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\tPRIMARY KEY (`id`) USING BTREE\n" +
                ")\n" +
                "COLLATE='utf8mb4_0900_ai_ci'\n" +
                "ENGINE=InnoDB\n" +
                ";")

        val rs = mysql.query("SELECT * FROM mqp;")
        mysql.execute("UPDATE mqp SET boolean = 2 WHERE date >= '${sdf.format(Date())}';")
        while (rs?.next() == true){
            Bukkit.getPlayer(UUID.fromString(rs.getString("owner")))?.let {
                itemFromBase64(rs.getString("item"))?.let { it1 -> MQPData(it, it1,
                rs.getInt("amount"),
                rs.getInt("price"),
//                    LocalDate.of(rs.getString("date").split("-")[0].toInt(),
//                            rs.getString("date").split("-")[1].toInt(),
//                            rs.getString("date").split("-")[2].toInt()),
                rs.getDate("date"),
                rs.getInt("boolean")) } }?.let { datamap.put(rs.getInt("id"),it) }


        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player)return true
        if (!sender.hasPermission("mq.command")){
            if (!sender.hasPermission("mq.op")) {
                sender.sendmsg("§4クエストの権限がない！")
                return true
            }
        }
        if (!sender.isOp && !sender.hasPermission("mq.op")){
            if (!enable){
                sender.sendmsg("§4クエストが閉まってる！")
                return true
            }
        }

        if (args.isEmpty()) {
            val inv = Bukkit.createInventory(null,54, Component.text("$prefix 依頼一覧 page 1"))
            var int = 0
            for (data in datamap){
                if (inv.getItem(53) != null)break
                if (data.value.boolean > 0)continue
                if (data.value.owner == sender)continue
                val item = data.value.item.clone()
                val meta = item.itemMeta
                meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),
                        Component.text("§a§l締切日:${sdf.format(data.value.datetime)}").asComponent(),
                        Component.text("§6§l報酬:${data.value.price}").asComponent()))
                meta.persistentDataContainer.set(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER,data.key)
                item.itemMeta = meta
                inv.setItem(int,item)
                int += 1
            }
            sender.openInventory(inv)

            return true
        }
        when(args[0]){

            "tax"->{
                if (sender.hasPermission("mq.op") || sender.isOp){
                    if (args.size != 2)return true
                    config.set("tax",args[1].toDoubleOrNull()?:return true)
                    saveConfig()
                    sender.sendmsg("§btaxを${args[1].toDouble()}に設定しました")
                }
            }
            "mode"->{
                if (sender.hasPermission("mq.op") || sender.isOp){
                    return if (enable){
                        enable = false
                        config.set("mode",false)
                        saveConfig()
                        sender.sendmsg("§bmodeをoffにしました")
                        true
                    }else{
                        enable = true
                        config.set("mode",true)
                        saveConfig()
                        sender.sendmsg("§bmodeをonにしました")
                        true
                    }
                }
            }
            "help"->{
                sender.sendMessage("""
                    §a§l==========================Man10Quest===========================
                    §a/mq クエスト一覧を表示します(1キーで前ページ、2キーで後ページに行けます
                    §aまた、アイテムをクリックするとその納品boxが開かれます
                    §a/mq add (期日(1~12)) (個数(64~2304)) (報酬(10000~)) 手に持ったアイテムを依頼します
                    §a/mq order 自分の依頼を確認します(また、受取可能なものをクリックすると受け取れます)
                    §a§l==========================Man10Quest=========Author:tororo_1066
                """.trimIndent())
                if (sender.hasPermission("mq.op")){
                    sender.sendMessage("""
                        §c§l/mq mode モードを切り替えます
                        §c§l/mq tax (Double) 手数料を設定します(1ヵ月ごとに増える)
                        ${datamap.values}
                    """.trimIndent())

                }
            }

            "add" -> {
                if (args.size != 4) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~2304) (報酬10000~)")
                    return true
                }
                if (args[1].toIntOrNull() == null || args[2].toIntOrNull() == null || args[3].toIntOrNull() == null) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~2304) (報酬10000~)")
                    return true
                }
                if (args[1].toInt() !in 1..12) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~2304) (報酬10000~)")
                    return true
                }
                if (args[2].toInt() !in 64..2304) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~2304) (報酬10000~)")
                    return true
                }
                if (args[3].toInt() < 10000) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数64~2304) (報酬10000~)")
                    return true
                }

                if (sender.inventory.itemInMainHand.type == Material.AIR) {
                    sender.sendmsg("手にアイテムを持ってください！")
                    return true
                }
                sender.sendmsg("依頼を出しています...")
                var int = 0
                for (d in datamap){
                    if (d.value.owner == sender)int += 1
                }
                if (int >= 54){
                    sender.sendmsg("§4依頼を出した件数が54件以上です")
                    return true
                }
                if (vault.getBalance(sender.uniqueId) < args[3].toDouble() + tax * args[1].toDouble()) {
                    sender.sendmsg("§4所持金が不足しています")
                    return true
                }
                es.execute {

                    val cal = Calendar.getInstance()
                    cal.time = Date()

                    cal.add(Calendar.MONTH,args[1].toInt())

                    val mysql = MySQLManager(this, "mqqQuestAdd")
                    mysql.execute("INSERT INTO mqp (owner, item, amount, price, date, boolean) " +
                            "VALUES ('${sender.uniqueId}', " +
                            "'${itemToBase64(sender.inventory.itemInMainHand)}', " +
                            "${args[2].toInt()}, " +
                            "${args[3].toInt()}, " +
                            "'${sdf.format(cal.time)}', 0);")

                    val rs = mysql.query("SELECT id FROM mqp ORDER BY id DESC LIMIT 1;")

                    if (rs != null) {

                        vault.withdraw(sender.uniqueId,args[3].toDouble() + tax * args[1].toDouble())
                        while (rs.next()){
                            datamap[rs.getInt("id")] = MQPData(sender, sender.inventory.itemInMainHand,
                                    args[2].toInt(), args[3].toInt(),
                                    rs.getDate("datetime"),
                                    0)
                        }

                        rs.close()

                        sender.sendmsg("§a依頼を出すことに成功しました！")
                        Bukkit.getScheduler().runTask(this, Runnable {
                            Bukkit.broadcastMessage("$prefix §d種類:${sender.inventory.itemInMainHand.type.name}" +
                                    "(§r${sender.inventory.itemInMainHand.itemMeta.displayName}§d)§fが§b個数:${args[2].toInt()}で出されました！" +
                                    "§a(期日:${sdf.format(cal.time)}まで)" +
                                    "§6(報酬:${args[3].toInt()})")
                        })
                    } else {
                        sender.sendmsg("§4依頼を出すことに失敗しました")
                    }
                    mysql.close()
                    return@execute
                }
            }

            "order"->{
                val inv = Bukkit.createInventory(null,54, Component.text("$prefix 自分の依頼"))
                var int = 0
                for (data in datamap){
                    if (data.value.owner != sender)continue
                    if (data.value.boolean == 2)continue
                    val item = data.value.item.clone()
                    val meta = item.itemMeta
                    meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),
                            Component.text("§a§l締切日:${sdf.format(data.value.datetime)}").asComponent(),
                            Component.text("§6§l報酬:${data.value.price}").asComponent(),
                            Component.text("§d§l受け取り:${if (data.value.boolean == 1){"可能"}else{"まだ"}}")))
                    meta.persistentDataContainer.set(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER,data.key)
                    item.itemMeta = meta
                    inv.setItem(int,item)
                    int += 1
                }
                sender.openInventory(inv)
            }
            "getorderitem","orderitem"->{
                if (args.size != 2)return true
                try {
                    if (datamap[args[1].toIntOrNull()?:return true]?.boolean!! == 0 || datamap[args[1].toInt()]?.owner != sender){
                        sender.sendmsg("§4このアイテムは受け取り出来ません")
                        return true
                    }
                }catch (e : NullPointerException){
                    sender.sendmsg("§4このアイテムは受け取り出来ません")
                    return true
                }

                val inv = Bukkit.createInventory(null,36, Component.text("$prefix 受取box id: ${args[1].toInt()}"))

                val item = datamap[args[1].toInt()]?.item?.clone()
                var amount = datamap[args[1].toInt()]?.amount!!
                amount/=64
                var littleamount = datamap[args[1].toInt()]?.amount!!
                littleamount%=64
                item?.amount = 64
                for (int in 1..amount){
                    inv.addItem(item!!)
                }
                item?.amount = littleamount
                inv.addItem(item!!)

                sender.openInventory(inv)
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
                    p.openInventory(pageopen(p,page-1))
                }
                1 ->{
                    if (datamap.size < page * 53)return
                    p.openInventory(pageopen(p,page+1))
                }
            }
            if (e.currentItem != null && e.currentItem?.hasItemMeta() == true && e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER) != null){

                val inv = Bukkit.createInventory(null,36, Component.text("$prefix 納品box id: ${e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER)} (インベントリを閉じて納品する)"))
                p.openInventory(inv)
            }
            return
        }
        if (e.view.title().toString().contains("自分の依頼") && e.clickedInventory?.type != InventoryType.PLAYER){
            e.isCancelled = true
            Bukkit.dispatchCommand(p,"mq orderitem ${e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER)}")
        }
        if (p.openInventory.title().toString().contains("受取box") && e.clickedInventory?.type == InventoryType.PLAYER){
            e.isCancelled = true
        }
    }

    @EventHandler
    fun close(e : InventoryCloseEvent){
        if (!e.view.title().toString().contains(prefix))return
        val p = e.player as Player
        if (e.view.title().toString().contains("納品box")){
            val id = e.view.title().toString().split(" ")[3].toInt()
            var amount = 0
            for (item in e.inventory.contents){
                if (item == null || item.type == Material.AIR)continue
                if (datamap[id]?.item?.isSimilar(item) == true){
                    amount += item.amount
                }else{
                    p.world.dropItemNaturally(p.location,item)
                }
            }
            if (amount >= datamap[id]?.amount!!){
                datamap[id]?.price?.toDouble()?.let { vault.deposit(p.uniqueId, it) }
                var dropamountstack = amount- datamap[id]?.amount!!
                dropamountstack /= 64
                val dropamount = amount- datamap[id]?.amount!! -(dropamountstack*64)
                val stackamount = datamap[id]?.item?.clone()!!

                stackamount.amount = 64
                if (dropamountstack != 0){
                    for (d in 1..dropamountstack){
                        p.world.dropItemNaturally(p.location,stackamount)
                    }
                }
                if (dropamount != 0){
                    stackamount.amount = dropamount
                    p.world.dropItemNaturally(p.location,stackamount)
                }
                es.execute {
                    val mysql = MySQLManager(this,"mqpQuestSuccess")
                    mysql.execute("UPDATE mqp SET boolean = 1 WHERE id = ${id};")
                    val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,datamap[id]?.amount!!,datamap[id]?.price!!,datamap[id]?.datetime!!,1)
                    datamap.remove(id)
                    datamap[id] = savedata
                    mysql.close()
                    return@execute
                }
                p.sendmsg("§a§lクエスト完了！§6§l${datamap[id]?.price}円§5§lを受け取りました！")
                return
            }else{
                for (item in e.inventory.contents){
                    if (item == null)continue
                    if (datamap[id]?.item != item)continue
                    p.world.dropItemNaturally(p.location,item)
                }
            }

        }
        if (e.view.title().toString().contains("受取box")){
            val id = e.view.title().toString().split(" ")[3].replace(",","").replace("\"","").toInt()
            var amount = 0
            for (item in e.inventory.contents){
                if (item == null)continue
                amount += item.amount
            }

            es.execute {
                val mysql = MySQLManager(this,"mqpCloseMenu")
                if (amount == 0){
                    datamap.remove(id)
                    mysql.execute("UPDATE mqp SET boolean = 2 WHERE id = $id;")
                    return@execute
                }
                mysql.execute("UPDATE mqp SET amount = $amount WHERE id = $id;")
                mysql.close()
                datamap.remove(id)
                datamap[id] = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,amount,datamap[id]?.price!!,datamap[id]?.datetime!!,datamap[id]?.boolean!!)
                return@execute
            }
            return
        }
    }


    private fun pageopen(p : Player, i : Int): Inventory {

        val inv = Bukkit.createInventory(null,54, Component.text("$prefix 依頼一覧 page $i"))
        var int = 0
        for (data in datamap){
            if (int < (i-1)*54){
                int+=1
                continue
            }
            if (inv.getItem(53) != null)break
            if (data.value.boolean > 0)continue
            if (data.value.owner == p)continue
            val item = data.value.item.clone()
            val meta = item.itemMeta
            meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),
                    Component.text("§a§l締切日:${sdf.format(data.value.datetime)}").asComponent(),
                    Component.text("§6§l報酬:${data.value.price}").asComponent()))
            meta.persistentDataContainer.set(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER,data.key)
            item.itemMeta = meta
            inv.setItem(int-((i-1)*54),item)
            int += 1
        }

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