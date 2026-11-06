/* Exercises the actual error writer and probe's loader/failure cleanup on a host.
 * Mock Vulkan commands do not establish any hardware rendering result. */
#define _GNU_SOURCE
#include <assert.h>
#include <stdarg.h>
#include <sys/wait.h>
#define dlopen test_dlopen
#define dlsym test_dlsym
#define dlclose test_dlclose
#define dladdr test_dladdr
#include "../native/xhyn_v02.c"
#undef dlopen
#undef dlsym
#undef dlclose
#undef dladdr

static int mode, closed, destroyed_instance, destroyed_surface, destroyed_device, released_window, created_device;
static unsigned char *engine;
static const char *characters(JNIEnv *env,jstring value,jboolean *copy){(void)env;(void)copy;return (const char *)value;}
static void release_chars(JNIEnv *env,jstring value,const char *chars){(void)env;(void)value;(void)chars;}
static jstring new_string(JNIEnv *env,const char *value){(void)env;return (jstring)strdup(value);}
static const struct JNINativeInterface_ interface = {.GetStringUTFChars=characters,.ReleaseStringUTFChars=release_chars,.NewStringUTF=new_string};
static JNIEnv environment = &interface;
int __android_log_print(int priority,const char *tag,const char *format,...){(void)priority;(void)tag;(void)format;return 0;}
ANativeWindow *ANativeWindow_fromSurface(JNIEnv *env,jobject surface){(void)env;(void)surface;return mode==5?NULL:(ANativeWindow *)1;}
int32_t ANativeWindow_getWidth(ANativeWindow *w){(void)w;return 480;}
int32_t ANativeWindow_getHeight(ANativeWindow *w){(void)w;return 270;}
void ANativeWindow_release(ANativeWindow *w){(void)w;released_window++;}

static VkResult create_instance(const VkInstanceCreateInfo *info,const VkAllocationCallbacks *allocator,VkInstance *out){(void)allocator;assert(info->pApplicationInfo->apiVersion==VK_API_VERSION_1_1);if(mode==2)return VK_ERROR_INCOMPATIBLE_DRIVER;*out=(VkInstance)1;return VK_SUCCESS;}
static void destroy_i(VkInstance instance,const VkAllocationCallbacks *allocator){(void)instance;(void)allocator;destroyed_instance++;}
static VkResult enumerate(VkInstance i,uint32_t *count,VkPhysicalDevice *p){(void)i;if(mode==3){*count=0;return VK_SUCCESS;}*count=1;*p=(VkPhysicalDevice)1;return VK_SUCCESS;}
static void properties(VkPhysicalDevice p,VkPhysicalDeviceProperties *out){(void)p;out->vendorID=0x13b5;out->deviceID=57;out->apiVersion=VK_API_VERSION_1_1;strcpy(out->deviceName,"Mock Mali \"GPU\"");}
static void features(VkPhysicalDevice p,VkPhysicalDeviceFeatures *out){(void)p;out->dualSrcBlend=VK_FALSE;out->textureCompressionBC=VK_FALSE;}
static void properties2(VkPhysicalDevice p,VkPhysicalDeviceProperties2 *out){(void)p;VkPhysicalDeviceDriverProperties *driver=out->pNext;driver->driverID=VK_DRIVER_ID_MESA_PANVK;strcpy(driver->driverName,"panvk");}
static void features2(VkPhysicalDevice p,VkPhysicalDeviceFeatures2 *out){(void)p;VkPhysicalDeviceExtendedDynamicState3FeaturesEXT *dynamic=out->pNext;dynamic->extendedDynamicState3SampleLocationsEnable=VK_FALSE;}
static VkResult create_surface(VkInstance i,const VkAndroidSurfaceCreateInfoKHR *info,const VkAllocationCallbacks *a,VkSurfaceKHR *surface){(void)i;(void)info;(void)a;*surface=(VkSurfaceKHR)1;return VK_SUCCESS;}
static void destroy_s(VkInstance i,VkSurfaceKHR s,const VkAllocationCallbacks *a){(void)i;(void)s;(void)a;destroyed_surface++;}
static void queue_properties(VkPhysicalDevice p,uint32_t *count,VkQueueFamilyProperties *out){(void)p;*count=1;if(out)out[0].queueFlags=VK_QUEUE_GRAPHICS_BIT;}
static VkResult surface_support(VkPhysicalDevice p,uint32_t q,VkSurfaceKHR s,VkBool32 *out){(void)p;(void)q;(void)s;*out=mode==6?VK_FALSE:VK_TRUE;return VK_SUCCESS;}
static VkResult create_device(VkPhysicalDevice p,const VkDeviceCreateInfo *info,const VkAllocationCallbacks *a,VkDevice *out){(void)p;(void)a;assert(info->enabledExtensionCount==1);created_device++;*out=(VkDevice)1;return VK_SUCCESS;}
static void destroy_d(VkDevice d,const VkAllocationCallbacks *a){(void)d;(void)a;destroyed_device++;}
static VkResult idle(VkDevice d){(void)d;return VK_SUCCESS;}
static void unused(void){}
static PFN_vkVoidFunction device_proc(VkDevice d,const char *name){(void)d;if(!strcmp(name,"vkDestroyDevice"))return(PFN_vkVoidFunction)destroy_d;if(!strcmp(name,"vkDeviceWaitIdle"))return(PFN_vkVoidFunction)idle;if(!strcmp(name,"vkGetDeviceQueue"))return unused;return NULL;}
static PFN_vkVoidFunction instance_proc(VkInstance i,const char *name){(void)i;
#define RETURN(n,f) if(!strcmp(name,#n))return(PFN_vkVoidFunction)f
 RETURN(vkCreateInstance,create_instance); RETURN(vkDestroyInstance,destroy_i); RETURN(vkEnumeratePhysicalDevices,enumerate); RETURN(vkGetPhysicalDeviceProperties,properties); RETURN(vkGetPhysicalDeviceFeatures,features); RETURN(vkGetPhysicalDeviceProperties2,properties2); RETURN(vkGetPhysicalDeviceFeatures2,features2);
 if(mode==4)return NULL;
 RETURN(vkCreateAndroidSurfaceKHR,create_surface); RETURN(vkDestroySurfaceKHR,destroy_s); RETURN(vkGetPhysicalDeviceQueueFamilyProperties,queue_properties); RETURN(vkGetPhysicalDeviceSurfaceSupportKHR,surface_support); RETURN(vkCreateDevice,create_device); RETURN(vkGetDeviceProcAddr,device_proc);
 RETURN(vkGetPhysicalDeviceSurfaceCapabilitiesKHR,unused); RETURN(vkGetPhysicalDeviceSurfaceFormatsKHR,unused);
 return NULL;
}
void *test_dlopen(const char *path,int flags){(void)path;if(mode==1)return NULL;return(void *)(uintptr_t)((flags&RTLD_NOLOAD)?2:1);}
void *test_dlsym(void *handle,const char *name){(void)handle;if(!strcmp(name,"SDL_main"))return engine;if(!strcmp(name,"vkGetInstanceProcAddr"))return(void *)instance_proc;return NULL;}
int test_dlclose(void *handle){(void)handle;closed++;return 0;}
int test_dladdr(const void *address,Dl_info *out){memset(out,0,sizeof(*out));if(address==engine){out->dli_fbase=engine;return 1;}return 0;}

#include "native_observer_host.h"
int main(int argc,char **argv){assert(argc==2);int checks=0;
 for(mode=0;mode<=7;mode++){
  closed=destroyed_instance=destroyed_surface=destroyed_device=released_window=created_device=0;
  char *result=(char *)probe(&environment,mode==0?NULL:(jobject)1);
  if(mode==0){assert(strstr(result,"IDENTIFIED"));assert(strstr(result,"Mock Mali \\\"GPU\\\""));assert(strstr(result,"\"driverId\":20"));assert(strstr(result,"\"dualSrcBlend\":false"));assert(!created_device);checks+=5;}
  else {assert(strstr(result,"FAILED"));checks++;}
  if(mode==1){assert(!closed);checks++;}else{assert(closed==1);checks++;}
  if(mode!=1&&mode!=2){assert(destroyed_instance==1);checks++;}
  if(mode==6){assert(destroyed_surface==1&&!created_device&&released_window==1);checks++;}
  if(mode==7){assert(created_device==1&&destroyed_device==1&&destroyed_surface==1&&released_window==1);assert(strstr(result,"vkCreateSwapchainKHR"));checks+=2;}
  free(result);
 }
 mode=0;engine=mmap(NULL,0x3000000,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);assert(engine!=MAP_FAILED);
 unsigned char expected[]={0xfd,0x7b,0xbf,0xa9,0xfd,0x03,0x00,0x91,0xe0,0x03,0x1f,0xaa,0xd0,0x56,0x22,0x94};
 assert(!Java_com_gtavsource_android_XhynV02Native_install(&environment,NULL,(jstring)argv[1],(jstring)"host-session"));checks++;
 memcpy(engine+0x2a3c784,expected,16);
 assert(Java_com_gtavsource_android_XhynV02Native_install(&environment,NULL,(jstring)argv[1],(jstring)"host-session"));checks++;
 uintptr_t stored_destination;memcpy(&stored_destination,engine+0x2a3c78c,sizeof(stored_destination));
 assert(*(uint32_t *)(engine+0x2a3c784)==0x58000050u);assert(stored_destination==(uintptr_t)controlled_stop);checks+=2;
 assert(Java_com_gtavsource_android_XhynV02Native_install(&environment,NULL,(jstring)argv[1],(jstring)"host-session"));checks++;
 pid_t child=fork();assert(child>=0);if(!child)controlled_stop(0x8392273d);int status;waitpid(child,&status,0);assert(WIFEXITED(status)&&WEXITSTATUS(status)==73);checks++;
 char file[PATH_MAX],text[16384];snprintf(file,sizeof(file),"%s/xhyn-native-error.txt",argv[1]);FILE *f=fopen(file,"rb");assert(f);size_t n=fread(text,1,sizeof(text)-1,f);text[n]=0;fclose(f);
 assert(strstr(text,"session=host-session\n")&&strstr(text,"error_code=0x8392273d\n")&&strstr(text,"Invalid resource mapping")&&strstr(text,"frame=0"));checks++;
 snprintf(file,sizeof(file),"%s/xhyn-native-error.part",argv[1]);assert(access(file,F_OK));checks++;
 checks+=observer_checks(argv[1]);
 printf("PASS %d native-component host checks; Vulkan forwarding/accounting/lifecycle mocks and real controlled exit/report I/O; no GPU rendering test\n",checks);
 return 0;
}
