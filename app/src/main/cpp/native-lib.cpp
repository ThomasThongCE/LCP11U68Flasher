#include <jni.h>
#include <string>
#include <android/log.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
//#include <cstdio>
//#include <cstdlib>
#include <cerrno>

#define TAG "nfc_programmer_jni"
#define RESET_DRIVER_PATH "/sys/class/gpio-boot-reset/nfc/"
#define RESET_DRIVER_FILE_PATH "/sys/class/gpio-boot-reset/nfc/mode"
#define NFC_DEVICE_PATH "/dev/block/sd"
#define MAXSIZE 256000


#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,    TAG, __VA_ARGS__)

/**
 * Finding device is mounted on which path (ex: /dev/block/sda, /dev/block/sdb)
 * @return last character of the path (ex:a, b, c)
 */
char findSdx()
{
    char name, path[20];
    size_t len = strlen(NFC_DEVICE_PATH);
    for (name = 'a'; name <= 'z'; ++name)
    {
        strcpy(path, NFC_DEVICE_PATH);
        path[len] = name;
        path[len+1] = '\0';
        if (access(path, F_OK) == 0)
            return name;
    }
    return '\0';
}

/**
 * Main function for flashing into <b>lcp11u68</b> <br>
 * - calculate firmware file size <br>
 * - load firmware file size <br>
 * - write down into mcu
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nfc_1programmer_FlashTask_program(JNIEnv *env, jobject instance,
                                                    jstring path_) {
    DIR *dir;
    FILE *srcFile;
    uint32_t fileSize;
    int fd;
    ssize_t retVal;
    const char *path = env->GetStringUTFChars(path_, 0);

    // Check is gpio-boot-reset driver is loaded
//    if ((dir = opendir(RESET_DRIVER_PATH)) == NULL)
//    {
//        LOGE("driver class not available, may be gpio-boot-reset driver not loaded\n");
//        closedir(dir);
//        return -1;
//    }
//    closedir(dir);

    // Load new update file
    srcFile = fopen(path, "r");
    if (srcFile)
        LOGI("firmware upgrade file %s loaded\n", path);
    else
    {
        LOGE("Can't load firmware upgrade file, error: %s\n", strerror(errno));
        return -1;
    }

    // Calculate file size
    fseek(srcFile, 0, SEEK_END);
    fileSize = ftell(srcFile);
    if (!fileSize)
    {
        LOGE("Error when calculate firmware upgrade file size: %s", strerror(errno));
    }
    if (fileSize > SIZE_MAX)
    {
        LOGE("Size of firmware upgrade is greater than size of disk. Stopped upgrade firmware");
        return -1;
    }
    LOGI("Size of firmware upgrade is %d\n", fileSize);
    fclose (srcFile);

    // Read data from update firmware file (binary mode)
    fd = open(path, O_RDONLY);
    if (fd == -1)
    {
        LOGE("Can't load firmware upgrade file in binary, error: %s\n", strerror(errno));

        return -1;
    }
    auto *buffer = (uint8_t *) malloc(fileSize);
    retVal = read(fd, buffer, fileSize);
    if (retVal != fileSize)
    {
        free(buffer);
        close(fd);
        LOGE ("Read firmware upgrade file error, error: %s\n", strerror(errno));
        return -1;
    }
    close(fd);
    LOGI ("Read firmware upgrade file complete \n");

    // find device
    char name = findSdx(), devPath[20];
    if ('\0' == name)
    {
        LOGE ("Can't find sdx file in /dev/block, did nfc boot into programming mode yet?, error: %s\n", strerror(errno));
        free(buffer);
        return -1;
    }

    // find device file
    size_t len = strlen(NFC_DEVICE_PATH);
    strcpy(devPath, NFC_DEVICE_PATH);
    devPath[len] = name;
    devPath[len+1] = '\0';
    LOGI ("Device found at %s \n", devPath);

    // open device file
    fd = open(devPath, O_RDWR);
    if (fd == -1)
    {
        LOGE ("Can't open nfc file %s, error: %s\n", devPath ,strerror(errno));
        free(buffer);
        return -1;
    }

    // Flashing device
    lseek(fd, 0x800, SEEK_SET);
    LOGI("Writing firmware data to disk\n");
    retVal = write(fd, buffer, fileSize);
    if (retVal != fileSize)
        LOGE ("file Write only %d, error: %s \n",retVal ,strerror(errno));
    fsync(fd);
    close(fd);
    LOGI("Finished!!!\n");

    //reset_device();

    free (buffer);
    env->ReleaseStringUTFChars(path_, path);
    return true;
}

/**
 * unmount partition using system call
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_example_nfc_1programmer_MainActivity_unmount(JNIEnv *env, jobject instance) {
    system("umount /mnt/media_rw/0000-0000");
}