## 简介
这是一个用于探究使用不同的软件结构划分，对于计算密集和延迟敏感的移动端应用软件的性能的影响的Android软件

## 功能介绍
这是一个OCR识别软件，可以从手机图库中选择一张图片，使用三种模式中的一种来识别图片中的文字：  
    - `LOCAL`：使用软件本地的 `PP-OCRv2.0` 模型进行OCR识别  
    - `REMOTE_FULL`：所有的图像处理和文字识别都在远程后端进行，前端只会上传图片到后端  
    - `REMOTE_HYBRID`：前端先进行预处理，再把结果发送到远程后端进行文字识别  
识别成功后，会显示识别用时：  
    - `LOCAL`模式会分别显示预处理和识别的用时  
    - `REMOTE_FULL`会显示远程处理总用时 `Network & Server Time` （从本地发送到完全接收返回的结果），和在服务器端进行的预处理和识别用时  
    - `REMOTE_HYBRID`会显示本地预处理用时，和远程处理总用时 `Network & Server Time` 和在服务器端的识别用时  
还可以复制识别结果，或者在开始识别之前，在当前图片中裁剪出要识别的区域。

## 一些说明
软件的OCR识别内核使用的是 `PaddleOCR`；  
软件本地的模型目前只验证了能够在Arm64架构上的真机和下面所述的虚拟机上运行，其他CPU结构的环境没有进行测试；  
服务器会自动下载 `PaddleOCR` 默认的、最新的中文模型，在软件开发时，此版本为 `PP-OCRv5_server`;  
使用了 `Goole Play Intel x86_64 Atom System Image, API=36.1` 虚拟机进行测试，还使用了Realme进行真机测试；

## 部署方法
- 后端部署：
    项目根目录下的 `server` 文件夹是远程后端所使用的服务器脚本。根据文件夹中的 `DockerFile` 构建容器，并安装 `requirements.txt` 中的依赖，最后运行 `server.py` 即可部署后端。
- 前端部署：
    在本地的项目根目录下新建 `local.properties`，在其中添加：
    ```
    # 请根据具体环境更改参数值
    sdk.dir=C\:\\Users\\xxx\\AppData\\Local\\Android\\Sdk
    DEV_API_URL="http://your.development.url/"
    PROD_API_URL="http://your.production.url/"
    ```
    然后使用Android Studio进行构建即可  
    注：build.gradle.kts 会在编译时读取 `local.properties` 文件中的 IP 地址，然后把地址写入 `BuildConfig.java` 中，运行时 `ApiService.kt` 直接从 `BuildConfig.BASE_URL` 中安全地获取服务器地址。

## 已知问题
- 在测试时，本人的后端使用的是阿里云2核2G的服务器，在使用 `REMOTE_FULL` 和 `REMOTE_HYBRID` 这两个方式识别时，会遇到:  
    ```Error: HTTP 500 INTERNAL SERVER ERROR```  
    或者  
    ```Error: unexpected end of stream```  
    只需要稍等片刻，再重新按开始识别，多试几次就可以了。  
    目前造成这个问题的原因未知，有待修复。
