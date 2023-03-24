package cn.evolvefield.mirai.onebot.util;

import cn.evolvefield.mirai.onebot.OneBotMirai;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Base64;
import java.util.Base64.Decoder;

/**
 * Description:
 * Author: cnlimiter
 * Date: 2022/10/4 2:43
 * Version: 1.0
 */
public class OnebotMsgParser {


    private static final PlainText MSG_EMPTY = new PlainText("");

    public static MessageChain messageToMiraiMessageChains(Bot bot, Contact contact, Object message, boolean raw){

        MessageChain msg = textToMessageInternal(bot,contact,message);
        return msg;
    }

    public static String toCQString(SingleMessage message){
        if (message instanceof PlainText text) return escape(text.getContent());

        else if (message instanceof At at) return "[CQ:at,qq=" +at.getTarget() + "]";

        else if (message instanceof Face face) return "[CQ:face,id=" + face.getId() + "]";

        else if (message instanceof VipFace vipFace) return "[CQ:vipface,id="+vipFace.getKind().getId()+",name=" +vipFace.getKind().getName()+ ",count=" +vipFace.getCount()+ "]";

        else if (message instanceof PokeMessage pokeMessage) return "[CQ:poke,id=" +pokeMessage.getId()+ ",type= "+pokeMessage.getPokeType()+" ,name="+pokeMessage.getName()+"]";

        else if (message instanceof AtAll all) return "[CQ:at,qq=all]";

        else if (message instanceof Image image) return "[CQ:image,file="+image.getImageId()+",url="+escape(Image.queryUrl(image))+"]";

        else if (message instanceof FlashImage flashImage) return "[CQ:image,file="+flashImage.getImage().getImageId()+",url="+escape(Image.queryUrl(flashImage.getImage()))+",type=flash]";

        else if (message instanceof ServiceMessage serviceMessage){
            if (serviceMessage.getContent().contains("xml version")) return "[CQ:xml,data="+escape(serviceMessage.getContent())+"]";
            else return "[CQ:json,data="+escape(serviceMessage.getContent())+"]";
        }
        else if (message instanceof LightApp app) return "[CQ:json,data="+escape(app.getContent())+"]";

        else if (message instanceof MessageSource) return "";

        else if (message instanceof QuoteReply quoteReply) return "[CQ:reply,id="+DataBaseUtils.toMessageId(quoteReply.getSource().getInternalIds(), quoteReply.getSource().getBotId(), quoteReply.getSource().getFromId())+"]";

        else if (message instanceof OnlineAudio audio) return "[CQ:record,url="+escape(audio.getUrlForDownload())+",file="+ Arrays.toString(audio.getFileMd5()) +"]";

        else if (message instanceof Audio audio) return "[CQ:record,url=,file="+ Arrays.toString(audio.getFileMd5()) +"]";

        else return "此处消息的转义尚未被插件支持";

    }

    private static Matcher regexMatcher(String regex, String text) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches()) {
            return matcher;
        } else {
            return null;
        }
    }

    private static JSONArray stringToMsgChain(String msg) {
        String CQ_CODE_SPLIT = "(?<=\\[CQ:[^]]{1,99999}])|(?=\\[CQ:[^]]{1,99999}])";
        String CQ_CODE_REGEX = "\\[CQ:([^,\\[\\]]+)((?:,[^,=\\[\\]]+=[^,\\[\\]]*)*)]";
        var array = new JSONArray();
        try {
            Arrays.stream(msg.split(CQ_CODE_SPLIT)).filter(s -> !s.isEmpty()).forEach(s -> {
                var matcher = regexMatcher(CQ_CODE_REGEX, s);
                var object = new JSONObject();
                var params = new JSONObject();
                if (matcher == null) {
                    object.put("type","text");
                    params.put("text", unescape_t(s));
                } else {
                    object.put("type", matcher.group(1));
                    Arrays.stream(matcher.group(2).split(",")).filter(args -> !args.isEmpty()).forEach(args -> {
                        var k = args.substring(0, args.indexOf("="));
                        var v = unescape(args.substring(args.indexOf("=") + 1));
                        params.put(k, v);
                    });
                }
                object.put("data", params);
                array.add(object);
            });
        } catch (Exception e) {
            return null;
        }
        return array;
    }


    private static String escape(String msg){
        return msg.replace("&", "&amp;")
                .replace("[", "&#91;")
                .replace("]", "&#93;");
//                .replace(",", "&#44;");
    }

    private static String unescape(String msg){
        return msg.replace("&amp;", "&")
                .replace("&#91;", "[")
                .replace("&#93;", "]")
                .replace("&#44;", ",");
    }

    private static String unescape_t(String msg){
        return msg.replace("&amp;", "&")
                .replace("&#91;", "[")
                .replace("&#93;", "]");
    }

    private static HashMap<String, String> toMap(String msg){
        var map = new HashMap<String, String>();
        Arrays.stream(msg.split(",")).forEach(
                s -> {
                    var parts = s.split("=",  2);
                    map.put(parts[0].trim(), unescape(parts[1]));
                }

        );
        return map;
    }

    private static MessageChain textToMessageInternal(Bot bot, Contact contact, Object messages) {
        if (messages instanceof String msgs){
            MessageChainBuilder msgChainBuilder = new MessageChainBuilder();
            JSONArray jsonArray = stringToMsgChain(msgs);
            for (Object msg:jsonArray) {
                Message msg_t = convertToMiraiMessage(bot,contact, (String) ((JSONObject)msg).get("type"), (Map<String, String>) ((JSONObject)msg).get("data"));
                msgChainBuilder.append(msg_t);
            }
            return msgChainBuilder.build();
        }
        else if (messages instanceof JSONArray jsonArray){
            MessageChainBuilder msgChainBuilder = new MessageChainBuilder();
            for (Object jsonObj:jsonArray) {
                var type = ((JSONObject)jsonObj).getJSONObject("type").toString();
                JSONObject data = ((JSONObject)jsonObj).getJSONObject("data");
                Map<String, String> args = new HashMap<>();
                data.forEach((s, o) -> args.put(s, (String) o));
                msgChainBuilder.append(convertToMiraiMessage(bot, contact, type, args));
            }
            return msgChainBuilder.build();
        }
        else return null;
    }

    private static byte[] readInputStream(InputStream inputStream)
            throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }


    private static Message convertToMiraiMessage(Bot bot, Contact contact, String type, Map<String, String> args){
        switch (type) {
            case "at" -> {
                if ("all".equals(args.get("qq"))) {
                    return AtAll.INSTANCE;
                } else {
                    if (contact instanceof Group) {
                        OneBotMirai.logger.debug("不能在私聊中发送 At。");
                        return MSG_EMPTY;
                    } else {
                        var member = contact.getBot().getFriend(Long.parseLong(args.get("qq")));
                        if (member == null) {
                            OneBotMirai.logger.debug(String.format("无法找到群员：%s", args.get("qq")));
                            return MSG_EMPTY;
                        } else {
                            return new At(member.getId());
                        }
                    }
                }
            }
            case "face" -> {
                return new Face(Integer.parseInt(args.get("id")));
            }
            case "text" -> {
                return new PlainText(args.get("text"));
            }
            case "emoji" -> {
                return new PlainText(new String(Character.toChars(Integer.parseInt(args.get("id")))));
            }
            case "image" -> {
                String file_url = args.get("file");
                if(file_url.startsWith("base64://")){
                    return contact.uploadImage(ExternalResource.create(Base64.getDecoder().decode(file_url.substring(9))));
                }else if(file_url.startsWith("http://") || file_url.startsWith("https://")) {
                    URL url = null;
                    try {
                        url = new URL(file_url);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        InputStream inputStream = conn.getInputStream();
                        byte[] getData = readInputStream(inputStream);
                        return contact.uploadImage(ExternalResource.create(getData));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return new PlainText("图片:"+file_url);
            }
            case "share" -> {
                return RichMessage.share(
                        args.get("url"),
                        args.get("title"),
                        args.get("content"),
                        args.get("image")
                );
            }
            case "record" -> {}
            case "contact" -> {
                if ("qq".equals(args.get("type"))) {
                    //return  RichMessageHelper.contactQQ(bot, args["id"]!!.toLong())
                } else {
                    //return RichMessageHelper.contactGroup(bot, args["id"]!!.toLong())
                }

            }
            case "music" -> {
//                switch (args.get("type")){
//                    case "qq" -> { }
//                }
//                return when (args["type"]) {
//                    "qq" -> QQMusic.send(args["id"]!!)
//                    "163" -> NeteaseMusic.send(args["id"]!!)
//                    "custom" -> Music.custom(
//                            args["url"]!!,
//                            args["audio"]!!,
//                            args["title"]!!,
//                            args["content"],
//                            args["image"]
//                )
//                else -> throw IllegalArgumentException("Custom music share not supported anymore")
//                }
            }
            case "shake" -> {return PokeMessage.ChuoYiChuo;}
            case "poke" -> {
                return Arrays.stream(PokeMessage.values).filter(
                        pokeMessage -> pokeMessage.getPokeType() == Integer.parseInt(args.get("type")) && pokeMessage.getId() == Integer.parseInt(args.get("id"))
                ).findFirst().orElseThrow();
            }
            case "nudge" -> {
                var target = Optional.of(args.get("qq")).orElseThrow();
                if (contact instanceof Group c) {
                    Optional.ofNullable(c.get(Long.parseLong(target))).orElseThrow().nudge().sendTo(c);
                } else {
                    Optional.ofNullable(contact).ifPresent( contact1 -> Optional.ofNullable(bot.getFriend(Long.parseLong(target))).orElseThrow().nudge().sendTo(contact) );
                }
                return MSG_EMPTY;
            }
            case "xml" -> {}
            case "json" -> {}
            case "reply" -> {

            }
            default -> {
                OneBotMirai.logger.debug("不支持的 CQ码：${type}");
            }
        }
        return MSG_EMPTY;

    }




//    File getDataFile(String type,String name){
//        arrayOf(
//                File(PluginBase.dataFolder, type).absolutePath + File.separatorChar,
//                "data" + File.separatorChar + type + File.separatorChar,
//                System.getProperty("java.library.path")
//                        .substringBefore(";") + File.separatorChar + "data" + File.separatorChar + type + File.separatorChar,
//                ""
//        ).forEach {
//            var f = File(it + name).absoluteFile;
//            if (f.exists()) {
//                return f;
//            }
//        }
//        return null;
//    }
}
