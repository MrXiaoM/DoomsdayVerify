# DoomsdayVerify

[![Release | 1.2 beta](https://img.shields.io/badge/Release-1.2%20beta-orange)](https://github.com/DoomsdaySociety/DoomsdayVerify/releases) [![](https://img.shields.io/badge/mcbbs-thread-brightgreen)](https://www.mcbbs.net/thread-1252309-1-1.html) [![](https://img.shields.io/badge/Minecraft-1.8--1.15-blue)]()
# 前言

因为我服务器需要让离线玩家进行正版验证来获得，  
并且想到现在有的正版验证插件好像都没微软登录功能。  
我太菜了，写不来，故使用了 HMCL 的登录方法，勉强可以进行微软登录。  
目前我只有正版微软账号，我试过用黑卡是可以进行mojang登录的，理论上是可以用的，  
我是使用 1.15 环境编写，使用 1.8 环境测试，所以理论上 1.8-1.15 都是适用的，  
更高的版本暂未尝试，欢迎尝试并反馈  
  
目前正在尝试兼容 1.17，代码已写，暂未测试，可自行编译尝试并在 issues 反馈结果获取 pull requests 提交修复后的代码

# 截图
![离线用户 LazyCat 登录 LittleCatX 的正版账号](https://attachment.mcbbs.net/data/myattachment/forum/202108/24/135209g6u7wlffuu2mmf9m.png)  

![离线用户 LittleCatX 登录 LittleCatX 的正版账号](https://attachment.mcbbs.net/data/myattachment/forum/202108/24/135209h0e2r2rawe3f8r0l.png)  

# 命令

* /dv mojang [邮箱] [密码] 验证mojang账号
* /ms 验证微软账号 (使用这个这么简短的命令的原因是我在1.8测试时，粘贴的链接会被聊天栏吃掉导致取不全链接中的代码，叫普通玩家来精准复制代码那是不可能的，于是不得不缩短命令，要是我闲得去搞 OAuth 或许会在以后的版本解决这个问题)
* /dv set [玩家] [次数] 设置玩家验证失败次数
* /dv reload 重载插件配置文件

# 权限

`doomsdayverify.mojang` 使用mojang账号验证的权限  
`doomsdayverify.microsoft` 使用微软账号验证的权限  
`doomsdayverify.settime` 设置玩家失败次数的权限  
`doomsdayverify.reload` 重载配置文件的权限  

# 变量

正版验证成功执行的命令支持使用 PAPI 变量，同时插件也注册了一些 PAPI 变量  
`%doomsdayverify_verified%` 玩家是否已验证，返回 yes 或者 no  
`%doomsdayverify_verified_是_否%` 玩家是否已验证 (是否文字可自行修改)  
`%doomsdayverify_fail_time%` 玩家验证失败次数  
`%doomsdayverify_fail_time_remaining%` 玩家验证失败剩余次数 (最大剩余次数减去失败次数)  

# 下载
[Releases](https://github.com/DoomsdaySociety/DoomsdayVerify/releases)
