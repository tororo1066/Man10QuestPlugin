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
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MQPData(val owner: Player, val item: ItemStack, val amount: Int, val price: Double, val datetime : Date, val boolean: Int)

class MQP : JavaPlugin(),Listener {
    private lateinit var es : ExecutorService
    private val prefix = "§f[§d§lMan10§a§lQuest§f]§r"
    private val datamap = ConcurrentHashMap<Int,MQPData>()
    private lateinit var vault : VaultManager
    private var tax = 0.00
    private var cancel = 0.00
    private var enable = true
    private var wait = false

    private val sdf = SimpleDateFormat("yyyy-MM-dd")


    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this,this)
        getCommand("mq")?.setExecutor(this)
        vault = VaultManager(this)
        es = Executors.newCachedThreadPool()
        enable = config.getBoolean("mode")
        tax = config.getDouble("tax")
        cancel = config.getDouble("cancel")
        val mysql = MySQLManager(this,"mqp")
        mysql.execute("CREATE TABLE `mqp` IF NOT EXISTS (\n" +
                "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
                "\t`owner` VARCHAR(36) NOT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`item` LONGTEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`amount` INT NULL,\n" +
                "\t`price` BIGINT NULL,\n" +
                "\t`date` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\t`boolean` TEXT NULL COLLATE 'utf8mb4_0900_ai_ci',\n" +
                "\tPRIMARY KEY (`id`) USING BTREE\n" +
                ")\n" +
                "COLLATE='utf8mb4_0900_ai_ci'\n" +
                "ENGINE=InnoDB\n" +
                "AUTO_INCREMENT=101\n" +
                ";")

        mysql.execute("UPDATE mqp SET boolean = 3 WHERE date <= '${sdf.format(Date())}' AND boolean = 0;")
        val rs = mysql.query("SELECT * FROM mqp;")
        while (rs?.next() == true){
            Bukkit.getPlayer(UUID.fromString(rs.getString("owner")))?.let {
                itemFromBase64(rs.getString("item"))?.let { it1 -> MQPData(it, it1,
                        rs.getInt("amount"),
                        rs.getDouble("price"),
//                    LocalDate.of(rs.getString("date").split("-")[0].toInt(),
//                            rs.getString("date").split("-")[1].toInt(),
//                            rs.getString("date").split("-")[2].toInt()),
                        rs.getDate("date"),
                        rs.getInt("boolean")) } }?.let { datamap.put(rs.getInt("id"),it) }


        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player)return true
        if (wait){
            sender.sendmsg("§4少し待ってから再試行してください")
            return true
        }
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
            try {
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
            }catch (e : NullPointerException){
                reloaddata(sender)
            }

        }
        when(args[0]){

            "test"->{
                wait = !wait
                return true
            }
            "tax"->{
                if (sender.hasPermission("mq.op") || sender.isOp){
                    if (args.size != 2)return true
                    config.set("tax",args[1].toDoubleOrNull()?:return true)
                    saveConfig()
                    tax = args[1].toDouble()
                    sender.sendmsg("§btaxを${args[1].toDouble()}に設定しました")
                }
            }

            "canceltax"->{
                if (sender.hasPermission("mq.op") || sender.isOp){
                    if (args.size != 2)return true
                    config.set("cancel",args[1].toDoubleOrNull()?:return true)
                    saveConfig()
                    cancel = args[1].toDouble()
                    sender.sendmsg("§bcancelを${args[1].toDouble()}に設定しました")
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
                sender.sendMessage("§a§l==========================Man10Quest===========================")
                sender.sendMessage("§a/mq クエスト一覧を表示します(1キーで前ページ、2キーで後ページに行けます")
                sender.sendMessage("§a(また、アイテムをクリックするとその納品boxが開かれます)")
                sender.sendMessage("§a(そのboxにアイテムを必要個数入れるとクエスト達成となり、お金がもらえます)")
                sender.sendMessage("§a/mq order (期日(1~12)) (個数(64~2304)) (報酬(10000~)) 手に持ったアイテムを依頼します")
                sender.sendMessage("§a(また、手数料として期間(1ヶ月単位)*${tax}円分引かれます")
                sender.sendMessage("§a/mq list 自分の依頼を確認します(また、受取可能なものをクリックすると受け取れます)")
                sender.sendMessage("§a(さらに、シフトクリックすることでキャンセルができます)")
                sender.sendMessage("§a(キャンセルされた時に帰ってくる料金は報酬*${cancel}円です)")
                sender.sendMessage("§a§l==========================Man10Quest=========Author:tororo_1066")
                if (sender.hasPermission("mq.op")){
                    sender.sendMessage("§c§l/mq mode モードを切り替えます")
                    sender.sendMessage("§c§l/mq tax (Double) 手数料を設定します(1ヵ月ごとに増える)")
                    sender.sendMessage("§c§l/mq canceltax (Double) キャンセルまたは期限切れの時の返却金を設定します")
                    sender.sendMessage("§c§l(1が最大、0が最低)")
                }
                return true
            }



            "order" -> {

                if (args.size != 4) {
                    sender.sendmsg("/mq order (何か月(1~12)) (個数${sender.inventory.itemInMainHand.maxStackSize}~${sender.inventory.itemInMainHand.maxStackSize * 36}) (報酬10000~)")
                    return true
                }
                if (args[1].toIntOrNull() == null || args[2].toIntOrNull() == null || args[3].toIntOrNull() == null) {
                    sender.sendmsg("/mq order (何か月(1~12)) (個数${sender.inventory.itemInMainHand.maxStackSize}~${sender.inventory.itemInMainHand.maxStackSize * 36}) (報酬10000~)")
                    return true
                }
                if (args[1].toInt() !in 1..12) {
                    sender.sendmsg("/mq order (何か月(1~12)) (個数${sender.inventory.itemInMainHand.maxStackSize}~${sender.inventory.itemInMainHand.maxStackSize * 36}) (報酬10000~)")
                    return true
                }
                if (sender.inventory.itemInMainHand.type == Material.AIR) {
                    sender.sendmsg("手にアイテムを持ってください！")
                    return true
                }
                val item = sender.inventory.itemInMainHand

                if (args[2].toInt() !in sender.inventory.itemInMainHand.maxStackSize..sender.inventory.itemInMainHand.maxStackSize * 36){
                    sender.sendmsg("/mq order (何か月(1~12)) (個数${sender.inventory.itemInMainHand.maxStackSize}~${sender.inventory.itemInMainHand.maxStackSize * 36}) (報酬10000~)")
                    return true
                }
                if (args[3].toInt() < 10000) {
                    sender.sendmsg("/mq add (何か月(1~12)) (個数${sender.inventory.itemInMainHand.maxStackSize}~${sender.inventory.itemInMainHand.maxStackSize * 36}) (報酬10000~)")
                    return true
                }
                val damage = item.itemMeta as Damageable
                if (damage.hasDamage()){
                    sender.sendmsg("このアイテムは耐久値が減っています")
                    return true
                }


                sender.sendmsg("依頼を出しています...")
                var int = 0
                for (d in datamap){
                    if (d.value.owner == sender && d.value.boolean != 2)int += 1
                }
                if (int >= 54){
                    sender.sendmsg("§4依頼を出した件数が54件以上です")
                    return true
                }
                if (vault.getBalance(sender.uniqueId) < args[3].toDouble() + tax * args[1].toDouble()) {
                    sender.sendmsg("§4所持金が不足しています")
                    return true
                }

                val price = args[3].toDouble()
                val amount = args[2].toInt()
                val month = args[1].toInt()

                es.execute {

                    val cal = Calendar.getInstance()
                    cal.time = Date()

                    cal.add(Calendar.MONTH,month)

                    val mysql = MySQLManager(this, "mqqQuestAdd")
                    mysql.execute("INSERT INTO mqp (owner, item, amount, price, date, boolean) " +
                            "VALUES ('${sender.uniqueId}', " +
                            "'${itemToBase64(item)}', " +
                            "${amount}, " +
                            "${price}, " +
                            "'${sdf.format(cal.time)}', 0);")

                    val rs = mysql.query("SELECT id,date FROM mqp ORDER BY id DESC LIMIT 1;")

                    if (rs == null) {
                        sender.sendmsg("§4依頼を出すことに失敗しました")
                        return@execute
                    }

                    vault.withdraw(sender.uniqueId,price + tax * args[1].toDouble())

                    while (rs.next()){
                        datamap[rs.getInt("id")] = MQPData(sender, item,
                                amount, price,
                                rs.getDate("date"),
                                0)
                    }

                    rs.close()
                    mysql.close()

                    sender.sendmsg("§a依頼を出すことに成功しました！")

                    Bukkit.getScheduler().runTask(this, Runnable {
                        Bukkit.broadcastMessage("$prefix §d種類:${sender.inventory.itemInMainHand.type.name}" +
                                "(§r${sender.inventory.itemInMainHand.itemMeta.displayName}§d)§fが§b個数:${amount}で出されました！" +
                                "§a(期日:${sdf.format(cal.time)}まで)" +
                                "§6(報酬:${price})")
                    })

                    return@execute
                }
            }

            "list"->{
                val inv = Bukkit.createInventory(null,54, Component.text("$prefix 自分の依頼"))
                try {
                    var int = 0
                    for (data in datamap){
                        if (data.value.owner != sender)continue
                        if (data.value.boolean == 2)continue
                        if (data.value.boolean == 3){
                            es.execute {
                                val mysql = MySQLManager(this,"mqQuestFailed")
                                mysql.execute("UPDATE mqp SET boolean = 2 WHERE id = ${data.key}")
                                val id = data.key
                                val savedata = MQPData(data.value.owner,data.value.item,data.value.amount,data.value.price,data.value.datetime,2)
                                datamap.remove(id)
                                datamap[id] = savedata
                                vault.deposit(sender.uniqueId,data.value.price*cancel)
                                sender.sendmsg("§d期限切れのアイテムがあったのでお金を返却しました")
                                mysql.close()
                                return@execute
                            }
                            continue
                        }
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
                }catch (e : NullPointerException){
                    reloaddata(sender)
                }
                sender.openInventory(inv)
                return true
            }
            "getorderitem","orderitem"->{

                if (args.size != 2)return true
                try {
                    if (datamap[args[1].toIntOrNull()?:return true]?.boolean!! != 1 || datamap[args[1].toInt()]?.owner != sender){
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
            "cancel"->{
                if (args.size != 2)return true
                try {
                    if (datamap[args[1].toIntOrNull()?:return true]?.boolean!! != 0 || datamap[args[1].toInt()]?.owner != sender){
                        sender.sendmsg("§4このアイテムはキャンセルできません")
                        return true
                    }
                }catch (e : NullPointerException){
                    sender.sendmsg("§4ここのアイテムはキャンセルできません")
                    return true
                }

                es.execute {
                    val id = args[1].toInt()
                    val mysql = MySQLManager(this,"mqQuestCancel")
                    mysql.execute("UPDATE mqp SET boolean = 2 WHERE id = $id")
                    val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,datamap[id]?.amount!!,datamap[id]?.price!!,datamap[id]?.datetime!!,2)
                    datamap.remove(id)
                    datamap[id] = savedata
                    datamap[id]?.price?.times(cancel)?.let { vault.deposit(sender.uniqueId, it) }
                    sender.sendmsg("§dクエストをキャンセルしました")
                    mysql.close()
                    return@execute
                }
                return true


            }
        }
        return true
    }

    @EventHandler
    fun cliiik(e : PlayerInteractEvent){
        if (e.hand == EquipmentSlot.OFF_HAND){
            e.isCancelled = true
            return
        }
        if (e.item?.type == Material.NETHERITE_AXE){
            wait = !wait
            e.player.sendMessage("waitを変更しました")
        }
    }
    @EventHandler
    fun drop(e : PlayerDropItemEvent){
        if (e.player.openInventory.title().toString().contains("受取box") && e.player.openInventory.title().toString().contains(prefix))e.isCancelled = true
        return
    }

    @EventHandler
    fun click(e : InventoryClickEvent){
        if (!e.view.title().toString().contains(prefix))return
        val p = e.whoClicked as Player
        if (e.view.title().toString().contains("依頼一覧")){
            e.isCancelled = true
            if (wait){
                p.sendmsg("§4少し待ってから再試行してください")
                return
            }
            val page = e.view.title().toString().split(" ")[3].replace(",","").replace("\"","").toInt()
            when(e.hotbarButton){
                0 ->{
                    if (page == 1)return
                    pageopen(p,page-1)?.let { p.openInventory(it) }
                }
                1 ->{
                    var i = 0
                    for (data in datamap){
                        if (data.value.boolean == 0 && data.value.owner != p)i++
                    }
                    if (i < page * 53)return
                    pageopen(p,page+1)?.let { p.openInventory(it) }
                }
            }
            if (e.currentItem != null && e.currentItem?.hasItemMeta() == true && e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER) != null){

                val inv = Bukkit.createInventory(null,36, Component.text("$prefix 納品box id: ${e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER)} (インベントリを閉じて納品する)"))
                p.openInventory(inv)
            }
            return
        }
        if (e.view.title().toString().contains("自分の依頼")){
            if (e.clickedInventory?.type == InventoryType.PLAYER){
                e.isCancelled = true
                return
            }
            if (wait){
                e.isCancelled = true
                p.sendmsg("§4少し待ってから再試行してください")
                return
            }
            if (e.isShiftClick){
                e.isCancelled = true
                Bukkit.dispatchCommand(p,"mq cancel ${e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER)}")
            }else{
                e.isCancelled = true
                Bukkit.dispatchCommand(p,"mq orderitem ${e.currentItem?.itemMeta?.persistentDataContainer?.get(NamespacedKey(this,"mqid"), PersistentDataType.INTEGER)}")
            }
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
            if (wait){
                for (item in e.inventory.contents){
                    if (item == null || item.type == Material.AIR)continue
                    p.world.dropItemNaturally(p.location,item)
                }
                p.sendmsg("§4少し待ってから再試行してください")
                return
            }
            if (datamap[id]?.boolean != 0){
                if (e.inventory.contents.isEmpty())return
                for (item in e.inventory.contents){
                    p.world.dropItemNaturally(p.location,item)
                }
            }
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
                datamap[id]?.price?.let { vault.deposit(p.uniqueId, it) }
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
                if (datamap[id]?.owner?.isOnline!!)datamap[id]?.owner?.sendmsg("§a§lクエスト§r${datamap[id]?.item?.type?.name}(${datamap[id]?.item?.itemMeta?.displayName}§r)§b§lが完了されました！§d(/mq listから受け取りましょう！)")
                p.sendmsg("§a§lクエスト完了！§6§l${datamap[id]?.price}円§5§lを受け取りました！")
                return
            }else{
                for (item in e.inventory.contents){
                    if (item == null)continue
                    if (!datamap[id]?.item?.isSimilar(item)!!)continue
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
            if (wait){
                for (item in p.inventory.contents){
                    if (item == datamap[id]?.item){
                        if (item.amount >= amount){
                            item.amount = amount - item.amount
                            p.sendmsg("§4少し待ってから再試行してください")
                            return
                        }
                        amount-=item.amount
                        item.type = Material.AIR

                    }
                }
            }

            es.execute {
                val mysql = MySQLManager(this,"mqpCloseMenu")
                if (amount == 0){
                    val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,0,datamap[id]?.price!!,datamap[id]?.datetime!!,2)
                    datamap.remove(id)
                    datamap[id] = savedata
                    mysql.execute("UPDATE mqp SET boolean = 2 WHERE id = $id;")
                    return@execute
                }
                mysql.execute("UPDATE mqp SET amount = $amount WHERE id = $id;")
                mysql.close()
                val savedata = MQPData(datamap[id]?.owner!!,datamap[id]?.item!!,amount,datamap[id]?.price!!,datamap[id]?.datetime!!,datamap[id]?.boolean!!)
                datamap.remove(id)
                datamap[id] = savedata
                return@execute
            }
            return
        }
    }


    private fun pageopen(p : Player, i : Int): Inventory? {
        try {
            if (wait){
                p.sendmsg("§4少し待ってから再試行してください")
                return null
            }
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
        }catch (e : NullPointerException){
            reloaddata(p)
        }
        return null
    }

    private fun reloaddata(p : Player){
        wait = true
        p.sendmsg("§4データのエラーを検出しました")
        p.sendmsg("§4修正中...")
        val mysql = MySQLManager(this,"mqDataReload")
        val rs = mysql.query("SELECT * FROM mqp;")
        datamap.clear()
        while (rs?.next() == true){
            Bukkit.getPlayer(UUID.fromString(rs.getString("owner")))?.let {
                itemFromBase64(rs.getString("item"))?.let { it1 -> MQPData(it, it1,
                        rs.getInt("amount"),
                        rs.getDouble("price"),
                        rs.getDate("date"),
                        rs.getInt("boolean")) } }?.let { datamap.put(rs.getInt("id"),it) }
        }
        p.sendmsg("§aデータの修正が完了しました！")
        wait = false
        return
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