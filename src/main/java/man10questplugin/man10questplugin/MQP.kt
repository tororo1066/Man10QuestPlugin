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
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
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
import kotlin.collections.HashMap

class MQPData(val owner: Player, val item: ItemStack, val amount: Int, val price: Int, val month: Int, val day: Int, val boolean: Boolean)

class MQP : JavaPlugin(),Listener {

    private lateinit var es : ExecutorService
    private val prefix = "§f[§d§lMan10§a§lQuest§f]§r"
    private val datamap = HashMap<Int,MQPData>()
    private val vault = VaultManager(this)


    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this,this)
        getCommand("mq")?.setExecutor(this)
        es = Executors.newCachedThreadPool()
        val mysql = MySQLManager(this,"mqp")
        mysql.execute("CREATE TABLE IF NOT EXISTS `mqp` (\n" +
                "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
                "\t`owner` VARCHAR(36) NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`item` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`amount` INT NULL,\n" +
                "\t`price` INT NULL,\n" +
                "\t`month` INT NULL,\n" +
                "\t`day` INT NULL,\n" +
                "\t`boolean` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\tPRIMARY KEY (`id`) USING BTREE\n" +
                ")\n" +
                "COLLATE='utf8mb4_0900_ai_ci'\n" +
                "ENGINE=InnoDB\n" +
                "AUTO_INCREMENT=2\n" +
                ";")
        mysql.execute("DELETE FROM mqp WHERE month <= ${ZonedDateTime.now().monthValue} AND day <= ${ZonedDateTime.now().dayOfMonth};")
        val rs = mysql.query("SELECT * FROM mqp;")
        while (rs?.next() == true){
            Bukkit.getPlayer(UUID.fromString(rs.getString("owner")))?.let { itemFromBase64(rs.getString("item"))?.let { it1 -> MQPData(it, it1,rs.getInt("amount"),rs.getInt("price"),rs.getInt("month"),rs.getInt("day"),rs.getBoolean("boolean")) } }?.let { datamap.put(rs.getInt("id"),it) }
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player)return true
        if (args.isEmpty()) {
            val inv = Bukkit.createInventory(null,54, Component.text("$prefix 依頼一覧 page 1"))
            var int = 0
            for (data in datamap){
                if (inv.getItem(53) != null)break
                if (data.value.boolean)continue
                if (data.value.owner == sender)continue
                val item = data.value.item.clone()
                val meta = item.itemMeta
                meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),Component.text("§a§l締切日:${data.value.month}/${data.value.day}").asComponent(),Component.text("§6§l報酬:${data.value.price}").asComponent(),Component.text("id:${data.key}").asComponent(),Component.text("Man10QuestItem").asComponent()))
                item.itemMeta = meta
                inv.setItem(int,item)
                int += 1
            }
            sender.openInventory(inv)

            return true
        }
        when(args[0]){

            "help"->{
                sender.sendMessage("""
                    §a§l==========================Man10Quest===========================
                    §a/mq クエスト一覧を表示します(1キーで前ページ、2キーで後ページに行けます
                    §aまた、アイテムをクリックするとその納品boxが開かれます
                    §a/mq add (期日(1~12)) (個数(64~2304)) (報酬(10000~)) 手に持ったアイテムを依頼します
                    §a/mq order 自分の依頼を確認します(また、受取可能なものをクリックすると受け取れます)
                    §a§l==========================Man10Quest=========Author:tororo_1066
                   
                """.trimIndent())
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
                if (vault.getBalance(sender.uniqueId) < args[3].toDouble()) {
                    sender.sendmsg("§4所持金が不足しています")
                    return true
                }
                es.execute {
                    val mysql = MySQLManager(this, "mqqQuestAdd")
                    if (mysql.execute("INSERT INTO mqp (owner, item, amount, price, month, day, boolean) VALUES ('${sender.uniqueId}', '${itemToBase64(sender.inventory.itemInMainHand)}', ${args[2].toInt()}, ${args[3].toInt()}, ${ZonedDateTime.now().monthValue + args[1].toInt()}, ${ZonedDateTime.now().dayOfMonth}, 'false');")) {
                        vault.withdraw(sender.uniqueId,args[3].toDouble())
                        val rs = mysql.query("SELECT * FROM mqp")
                        while (rs?.next() == true){
                            if (rs.isLast){
                                datamap[rs.getInt("id")] = MQPData(sender, sender.inventory.itemInMainHand, args[2].toInt(), args[3].toInt(), ZonedDateTime.now().monthValue + args[1].toInt(), ZonedDateTime.now().dayOfMonth, false)
                            }
                        }

                        sender.sendmsg("§a依頼を出すことに成功しました！")
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
                    val item = data.value.item.clone()
                    val meta = item.itemMeta
                    meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),Component.text("§a§l締切日:${data.value.month}/${data.value.day}").asComponent(),Component.text("§6§l報酬:${data.value.price}").asComponent(), Component.text("§d§l受け取り:${if (data.value.boolean){"可能"}else{"まだ"}}"), Component.text("id:${data.key}"),Component.text("Man10QuestItem").asComponent()))
                    item.itemMeta = meta
                    inv.setItem(int,item)
                    int += 1
                }
                sender.openInventory(inv)
            }
            "getorderitem","orderitem"->{
                if (args.size != 2)return true
                try {
                    if (!datamap[args[1].toIntOrNull()?:return true]?.boolean!! || datamap[args[1].toInt()]?.owner != sender){
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
            if (e.currentItem != null && e.currentItem?.hasItemMeta() == true && e.currentItem?.itemMeta?.lore()?.get(4)?.toString()?.contains("Man10QuestItem") == true){

                val inv = Bukkit.createInventory(null,36, Component.text("$prefix 納品box id: ${e.currentItem?.itemMeta?.lore()?.get(3)?.toString()?.split("\"")?.get(1)?.replace("id:","")} (インベントリを閉じて納品する)"))
                p.openInventory(inv)
            }
            return
        }
        if (e.view.title().toString().contains("自分の依頼") && e.clickedInventory?.type != InventoryType.PLAYER){
            e.isCancelled = true
            Bukkit.dispatchCommand(p,"mq orderitem ${e.currentItem?.itemMeta?.lore()?.get(4)?.toString()?.split("\"")?.get(1)?.replace("id:","")?.toInt()}")
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
                    mysql.execute("UPDATE mqp SET boolean = 'true' WHERE id = ${id};")
                    val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,datamap[id]?.amount!!,datamap[id]?.price!!,datamap[id]?.month!!,datamap[id]?.day!!,true)
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
                    mysql.execute("DELETE FROM mqp WHERE id = $id;")
                    return@execute
                }
                mysql.execute("UPDATE mqp SET amount = $amount WHERE id = $id;")
                mysql.close()
                val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,amount,datamap[id]?.price!!,datamap[id]?.month!!,datamap[id]?.day!!,datamap[id]?.boolean!!)
                datamap.remove(id)
                datamap[id] = savedata
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
            if (data.value.boolean)continue
            if (data.value.owner == p)continue
            val item = data.value.item.clone()
            val meta = item.itemMeta
            meta.lore(mutableListOf(Component.text("§b§l必要数:${data.value.amount}").asComponent(),Component.text("§a§l締切日:${data.value.month}/${data.value.day}").asComponent(),Component.text("§6§l報酬:${data.value.price}").asComponent(),Component.text("id:${data.key}").asComponent(),Component.text("Man10QuestItem").asComponent()))
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