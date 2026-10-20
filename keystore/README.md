# 签名材料

`ddz-release.jks` 与根目录 `keystore.properties` 均被 `.gitignore` 忽略，**不会提交到 GitHub**。

生成方式：

```bash
keytool -genkeypair -v -keystore keystore/ddz-release.jks -alias ddz \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=YourName, OU=LanParty, O=YourName, L=Shanghai, ST=Shanghai, C=CN"
```

然后在根目录创建 `keystore.properties`：

```properties
storeFile=keystore/ddz-release.jks
storePassword=你的密码
keyAlias=ddz
keyPassword=你的密码
```

缺少 `keystore.properties` 时，`:androidApp:assembleRelease` 会自动退回 debug 签名，方便 CI 产出可安装包。

本机构建产物：

```
androidApp/build/outputs/apk/release/androidApp-release.apk
```

> 本项目本地交付包的签名指纹（SHA-256）：
> `57:80:C8:92:44:8D:B8:CB:B1:AD:E2:3E:28:A1:E5:D3:3F:EB:55:C6:7B:A6:1F:1B:DD:C9:07:3D:D7:2B:7A:0A`
