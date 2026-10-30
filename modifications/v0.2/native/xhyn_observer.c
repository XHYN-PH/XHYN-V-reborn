/* New-code observer. Included after xhyn_v02.c's Vulkan/JSON helpers.
 * Calls retain the selected loader's dispatch, allocation callbacks and results.
 * No feature bits, barriers, allocation sizes or resource lifetimes are changed. */
#include <pthread.h>
#include <stdatomic.h>
#include <errno.h>

enum obs_command { O_ALLOC, O_FREE, O_QUEUE, O_QUEUE2, O_PRESENT, O_SUBMIT,
    O_SUBMIT2, O_GRAPHICS, O_COMPUTE, O_SWAP, O_DESTROY_SWAP, O_DESTROY, O_COUNT };
static const char *const obs_names[O_COUNT] = {"vkAllocateMemory", "vkFreeMemory",
    "vkGetDeviceQueue", "vkGetDeviceQueue2", "vkQueuePresentKHR", "vkQueueSubmit",
    "vkQueueSubmit2", "vkCreateGraphicsPipelines", "vkCreateComputePipelines",
    "vkCreateSwapchainKHR", "vkDestroySwapchainKHR", "vkDestroyDevice"};
#define OBS_DEVICES 8
#define OBS_QUEUES 64
#define OBS_ALLOCATIONS 16384
#define OBS_INTERVALS 2048
struct obs_device { VkDevice handle; VkPhysicalDevice physical; VkInstance instance;
    PFN_vkVoidFunction functions[O_COUNT]; bool budget; };
struct obs_queue { VkQueue handle; VkDevice device; uint64_t last_present, last_cpu;
    uint64_t generation; uintptr_t cpu_thread; };
struct obs_allocation { VkDevice device; VkDeviceMemory memory; uint64_t bytes; };
struct obs_totals { uint64_t live, peak, allocations, frees, failed, untracked,
    presents, present_errors, submits, command_buffers, graphics, compute,
    pipeline_ns, swapchains, surface_errors, device_losses, intervals_overwritten,
    interval_count, interval_total_ns, interval_max_ns, capture_generation;
    int last_result; uint64_t heap_budget, heap_usage, device_local_heap;
    uint32_t vendor, device, api; char gpu[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE]; };
static pthread_mutex_t obs_lock = PTHREAD_MUTEX_INITIALIZER;
static struct obs_device obs_devices[OBS_DEVICES];
static struct obs_queue obs_queues[OBS_QUEUES];
static struct obs_allocation obs_allocations[OBS_ALLOCATIONS];
static struct obs_totals obs_stats;
static uint64_t obs_intervals[OBS_INTERVALS];
static PFN_vkGetInstanceProcAddr obs_get_original;
static _Atomic(PFN_vkGetDeviceProcAddr) obs_get_device_original;
static _Atomic(PFN_vkCreateDevice) obs_create_original;
static VkInstance obs_instance;
static PFN_vkVoidFunction obs_fallback[O_COUNT];
static atomic_bool obs_capture, obs_hints, obs_paused;
static atomic_int obs_target_fps;
static bool obs_hooked;
static void (*obs_hint_callback)(uint64_t, uint64_t);

static uint64_t obs_clock(clockid_t kind) {
    struct timespec value;
    return clock_gettime(kind, &value) ? 0 : (uint64_t)value.tv_sec * 1000000000u + value.tv_nsec;
}
static struct obs_device *obs_find_device(VkDevice device) {
    for (unsigned n=0; n<OBS_DEVICES; ++n) if (obs_devices[n].handle==device) return &obs_devices[n];
    return NULL;
}
static PFN_vkVoidFunction obs_function(VkDevice device, enum obs_command command) {
    pthread_mutex_lock(&obs_lock);
    struct obs_device *slot=obs_find_device(device);
    PFN_vkVoidFunction result=slot?slot->functions[command]:NULL;
    PFN_vkGetDeviceProcAddr get=obs_get_device_original;
    pthread_mutex_unlock(&obs_lock);
    return result?result:get?get(device,obs_names[command]):NULL;
}
static PFN_vkVoidFunction obs_queue_function(VkQueue queue, enum obs_command command, VkDevice *device) {
    *device=VK_NULL_HANDLE;
    pthread_mutex_lock(&obs_lock);
    for (unsigned n=0;n<OBS_QUEUES;++n) if(obs_queues[n].handle==queue){*device=obs_queues[n].device;break;}
    PFN_vkVoidFunction fallback=obs_fallback[command];
    pthread_mutex_unlock(&obs_lock);
    return *device?obs_function(*device,command):fallback;
}
static void obs_result(VkResult result) {
    if(result>=0)return;
    pthread_mutex_lock(&obs_lock);
    obs_stats.last_result=result;
    if(result==VK_ERROR_DEVICE_LOST)++obs_stats.device_losses;
    if(result==VK_ERROR_SURFACE_LOST_KHR||result==VK_ERROR_OUT_OF_DATE_KHR)++obs_stats.surface_errors;
    pthread_mutex_unlock(&obs_lock);
}
static size_t obs_memory_index(VkDevice device,VkDeviceMemory memory) {
    return (((uintptr_t)device>>4)^((uintptr_t)memory>>4))&(OBS_ALLOCATIONS-1);
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_allocate(VkDevice device,const VkMemoryAllocateInfo *info,const VkAllocationCallbacks *allocator,VkDeviceMemory *out) {
    PFN_vkAllocateMemory call=(PFN_vkAllocateMemory)obs_function(device,O_ALLOC);
    VkResult result=call(device,info,allocator,out);
    pthread_mutex_lock(&obs_lock);
    if(result==VK_SUCCESS){
        ++obs_stats.allocations;
        size_t index=obs_memory_index(device,*out),n;
        for(n=0;n<OBS_ALLOCATIONS;++n){struct obs_allocation *slot=&obs_allocations[(index+n)&(OBS_ALLOCATIONS-1)];
            if(!slot->memory){*slot=(struct obs_allocation){device,*out,info->allocationSize};
                obs_stats.live+=info->allocationSize;if(obs_stats.live>obs_stats.peak)obs_stats.peak=obs_stats.live;break;}}
        if(n==OBS_ALLOCATIONS)++obs_stats.untracked;
    }else{++obs_stats.failed;obs_stats.last_result=result;}
    pthread_mutex_unlock(&obs_lock);
    return result;
}
static VKAPI_ATTR void VKAPI_CALL obs_free(VkDevice device,VkDeviceMemory memory,const VkAllocationCallbacks *allocator) {
    PFN_vkFreeMemory call=(PFN_vkFreeMemory)obs_function(device,O_FREE);
    if(!memory){call(device,memory,allocator);return;}
    pthread_mutex_lock(&obs_lock);
    size_t index=obs_memory_index(device,memory);
    /* Deletions leave holes, so a miss must inspect the complete bounded table. */
    for(size_t n=0;n<OBS_ALLOCATIONS;++n){struct obs_allocation *slot=&obs_allocations[(index+n)&(OBS_ALLOCATIONS-1)];
        if(slot->device==device&&slot->memory==memory){obs_stats.live-=slot->bytes;*slot=(struct obs_allocation){0};++obs_stats.frees;break;}}
    pthread_mutex_unlock(&obs_lock);
    call(device,memory,allocator);
}
static void obs_remember_queue(VkDevice device,VkQueue queue) {
    if(!queue)return;
    pthread_mutex_lock(&obs_lock);
    for(unsigned n=0;n<OBS_QUEUES;++n)if(obs_queues[n].handle==queue){pthread_mutex_unlock(&obs_lock);return;}
    for(unsigned n=0;n<OBS_QUEUES;++n)if(!obs_queues[n].handle){obs_queues[n]=(struct obs_queue){.handle=queue,.device=device};break;}
    pthread_mutex_unlock(&obs_lock);
}
static VKAPI_ATTR void VKAPI_CALL obs_queue(VkDevice device,uint32_t family,uint32_t index,VkQueue *out) {
    ((PFN_vkGetDeviceQueue)obs_function(device,O_QUEUE))(device,family,index,out);obs_remember_queue(device,*out);
}
static VKAPI_ATTR void VKAPI_CALL obs_queue2(VkDevice device,const VkDeviceQueueInfo2 *info,VkQueue *out) {
    ((PFN_vkGetDeviceQueue2)obs_function(device,O_QUEUE2))(device,info,out);obs_remember_queue(device,*out);
}
static void obs_budget(VkDevice device) {
    pthread_mutex_lock(&obs_lock);
    struct obs_device *slot=obs_find_device(device);
    struct obs_device copy=slot?*slot:(struct obs_device){0};
    pthread_mutex_unlock(&obs_lock);
    if(!copy.budget||!obs_get_original)return;
    PFN_vkGetPhysicalDeviceMemoryProperties2 get=(PFN_vkGetPhysicalDeviceMemoryProperties2)obs_get_original(copy.instance,"vkGetPhysicalDeviceMemoryProperties2");
    if(!get)return;
    VkPhysicalDeviceMemoryBudgetPropertiesEXT budget={.sType=VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_BUDGET_PROPERTIES_EXT};
    VkPhysicalDeviceMemoryProperties2 props={.sType=VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2,.pNext=&budget};
    get(copy.physical,&props);
    uint64_t used=0,total=0;
    for(unsigned n=0;n<props.memoryProperties.memoryHeapCount;++n)if(props.memoryProperties.memoryHeaps[n].flags&VK_MEMORY_HEAP_DEVICE_LOCAL_BIT){used+=budget.heapUsage[n];total+=budget.heapBudget[n];}
    pthread_mutex_lock(&obs_lock);obs_stats.heap_budget=total;obs_stats.heap_usage=used;pthread_mutex_unlock(&obs_lock);
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_present(VkQueue queue,const VkPresentInfoKHR *info) {
    VkDevice device;PFN_vkQueuePresentKHR call=(PFN_vkQueuePresentKHR)obs_queue_function(queue,O_PRESENT,&device);
    bool capture=atomic_load(&obs_capture),hints=atomic_load(&obs_hints),paused=atomic_load(&obs_paused);
    uint64_t now=(capture||hints)?obs_clock(CLOCK_MONOTONIC):0;
    uint64_t cpu=hints?obs_clock(CLOCK_THREAD_CPUTIME_ID):0,work=0;
    bool budget=false;
    pthread_mutex_lock(&obs_lock);
    for(unsigned n=0;n<OBS_QUEUES;++n)if(obs_queues[n].handle==queue){struct obs_queue *q=&obs_queues[n];
        if(capture&&!paused&&q->last_present&&q->generation==obs_stats.capture_generation&&now>q->last_present){
            uint64_t interval=now-q->last_present;
            if(interval<10000000000ull){uint64_t index=obs_stats.interval_count++;
                obs_intervals[index%OBS_INTERVALS]=interval;if(index>=OBS_INTERVALS)++obs_stats.intervals_overwritten;
                obs_stats.interval_total_ns+=interval;if(interval>obs_stats.interval_max_ns)obs_stats.interval_max_ns=interval;}}
        if(hints&&!paused&&q->cpu_thread==(uintptr_t)pthread_self()&&q->last_cpu&&cpu>q->last_cpu)work=cpu-q->last_cpu;
        q->last_present=paused?0:now;q->last_cpu=paused?0:cpu;q->cpu_thread=(uintptr_t)pthread_self();q->generation=obs_stats.capture_generation;break;}
    if(capture)budget=obs_stats.presents%120==0;
    ++obs_stats.presents;pthread_mutex_unlock(&obs_lock);
    /* CPU time on this presentation thread excludes blocked/sleeping time. It
     * does not represent all game/render worker work or GPU execution time. */
    int fps=atomic_load(&obs_target_fps);
    if(work&&work<1000000000ull&&fps>0&&obs_hint_callback)obs_hint_callback(work,1000000000ull/(unsigned)fps);
    if(budget&&device)obs_budget(device);
    VkResult result=call(queue,info);obs_result(result);
    if(result<0){pthread_mutex_lock(&obs_lock);++obs_stats.present_errors;pthread_mutex_unlock(&obs_lock);}
    return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_submit(VkQueue queue,uint32_t count,const VkSubmitInfo *info,VkFence fence) {
    VkDevice device;PFN_vkQueueSubmit call=(PFN_vkQueueSubmit)obs_queue_function(queue,O_SUBMIT,&device);
    VkResult result=call(queue,count,info,fence);obs_result(result);
    pthread_mutex_lock(&obs_lock);++obs_stats.submits;for(unsigned n=0;n<count;++n)obs_stats.command_buffers+=info[n].commandBufferCount;pthread_mutex_unlock(&obs_lock);return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_submit2(VkQueue queue,uint32_t count,const VkSubmitInfo2 *info,VkFence fence) {
    VkDevice device;PFN_vkQueueSubmit2 call=(PFN_vkQueueSubmit2)obs_queue_function(queue,O_SUBMIT2,&device);
    VkResult result=call(queue,count,info,fence);obs_result(result);
    pthread_mutex_lock(&obs_lock);++obs_stats.submits;for(unsigned n=0;n<count;++n)obs_stats.command_buffers+=info[n].commandBufferInfoCount;pthread_mutex_unlock(&obs_lock);return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_graphics(VkDevice device,VkPipelineCache cache,uint32_t count,const VkGraphicsPipelineCreateInfo *info,const VkAllocationCallbacks *allocator,VkPipeline *out) {
    PFN_vkCreateGraphicsPipelines call=(PFN_vkCreateGraphicsPipelines)obs_function(device,O_GRAPHICS);uint64_t start=obs_clock(CLOCK_MONOTONIC);
    VkResult result=call(device,cache,count,info,allocator,out);obs_result(result);
    pthread_mutex_lock(&obs_lock);obs_stats.graphics+=count;obs_stats.pipeline_ns+=obs_clock(CLOCK_MONOTONIC)-start;pthread_mutex_unlock(&obs_lock);return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_compute(VkDevice device,VkPipelineCache cache,uint32_t count,const VkComputePipelineCreateInfo *info,const VkAllocationCallbacks *allocator,VkPipeline *out) {
    PFN_vkCreateComputePipelines call=(PFN_vkCreateComputePipelines)obs_function(device,O_COMPUTE);uint64_t start=obs_clock(CLOCK_MONOTONIC);
    VkResult result=call(device,cache,count,info,allocator,out);obs_result(result);
    pthread_mutex_lock(&obs_lock);obs_stats.compute+=count;obs_stats.pipeline_ns+=obs_clock(CLOCK_MONOTONIC)-start;pthread_mutex_unlock(&obs_lock);return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_swap(VkDevice device,const VkSwapchainCreateInfoKHR *info,const VkAllocationCallbacks *allocator,VkSwapchainKHR *out) {
    VkResult result=((PFN_vkCreateSwapchainKHR)obs_function(device,O_SWAP))(device,info,allocator,out);obs_result(result);
    if(result==VK_SUCCESS){pthread_mutex_lock(&obs_lock);++obs_stats.swapchains;pthread_mutex_unlock(&obs_lock);}return result;
}
static VKAPI_ATTR void VKAPI_CALL obs_destroy_swap(VkDevice device,VkSwapchainKHR swap,const VkAllocationCallbacks *allocator) {
    ((PFN_vkDestroySwapchainKHR)obs_function(device,O_DESTROY_SWAP))(device,swap,allocator);
    pthread_mutex_lock(&obs_lock);for(unsigned n=0;n<OBS_QUEUES;++n)if(obs_queues[n].device==device){obs_queues[n].last_present=obs_queues[n].last_cpu=0;}pthread_mutex_unlock(&obs_lock);
}
static VKAPI_ATTR void VKAPI_CALL obs_destroy_device(VkDevice device,const VkAllocationCallbacks *allocator) {
    PFN_vkDestroyDevice call=(PFN_vkDestroyDevice)obs_function(device,O_DESTROY);
    pthread_mutex_lock(&obs_lock);
    struct obs_device *slot=obs_find_device(device);if(slot)*slot=(struct obs_device){0};
    for(unsigned n=0;n<OBS_QUEUES;++n)if(obs_queues[n].device==device)obs_queues[n]=(struct obs_queue){0};
    for(unsigned n=0;n<OBS_ALLOCATIONS;++n)if(obs_allocations[n].device==device){obs_stats.live-=obs_allocations[n].bytes;obs_allocations[n]=(struct obs_allocation){0};}
    pthread_mutex_unlock(&obs_lock);
    call(device,allocator);
}
static const PFN_vkVoidFunction obs_wrappers[O_COUNT]={(PFN_vkVoidFunction)obs_allocate,(PFN_vkVoidFunction)obs_free,
    (PFN_vkVoidFunction)obs_queue,(PFN_vkVoidFunction)obs_queue2,(PFN_vkVoidFunction)obs_present,
    (PFN_vkVoidFunction)obs_submit,(PFN_vkVoidFunction)obs_submit2,(PFN_vkVoidFunction)obs_graphics,
    (PFN_vkVoidFunction)obs_compute,(PFN_vkVoidFunction)obs_swap,(PFN_vkVoidFunction)obs_destroy_swap,(PFN_vkVoidFunction)obs_destroy_device};
static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL obs_device_proc(VkDevice device,const char *name) {
    PFN_vkVoidFunction result=obs_get_device_original(device,name);
    if(!result)return NULL;
    pthread_mutex_lock(&obs_lock);struct obs_device *slot=obs_find_device(device);
    if(slot)for(unsigned n=0;n<O_COUNT;++n)if(!strcmp(name,obs_names[n])||(n==O_SUBMIT2&&!strcmp(name,"vkQueueSubmit2KHR"))){
        slot->functions[n]=result;obs_fallback[n]=result;result=obs_wrappers[n];break;}
    pthread_mutex_unlock(&obs_lock);return result;
}
static VKAPI_ATTR VkResult VKAPI_CALL obs_create_device(VkPhysicalDevice physical,const VkDeviceCreateInfo *info,const VkAllocationCallbacks *allocator,VkDevice *out) {
    VkResult result=obs_create_original(physical,info,allocator,out);obs_result(result);if(result!=VK_SUCCESS)return result;
    pthread_mutex_lock(&obs_lock);struct obs_device *slot=NULL;
    for(unsigned n=0;n<OBS_DEVICES;++n)if(!obs_devices[n].handle){slot=&obs_devices[n];*slot=(struct obs_device){.handle=*out,.physical=physical,.instance=obs_instance};break;}
    pthread_mutex_unlock(&obs_lock);
    if(!slot)return result; /* A full observer table never rejects a real device. */
    PFN_vkGetPhysicalDeviceProperties properties=(PFN_vkGetPhysicalDeviceProperties)obs_get_original(obs_instance,"vkGetPhysicalDeviceProperties");
    VkPhysicalDeviceProperties props={0};if(properties)properties(physical,&props);
    PFN_vkEnumerateDeviceExtensionProperties extensions=(PFN_vkEnumerateDeviceExtensionProperties)obs_get_original(obs_instance,"vkEnumerateDeviceExtensionProperties");
    uint32_t count=0;bool budget=false;
    if(extensions&&extensions(physical,NULL,&count,NULL)==VK_SUCCESS&&count<=512){VkExtensionProperties list[512];VkResult status=extensions(physical,NULL,&count,list);
        if(status==VK_SUCCESS||status==VK_INCOMPLETE)for(unsigned n=0;n<count&&n<512;++n)if(!strcmp(list[n].extensionName,VK_EXT_MEMORY_BUDGET_EXTENSION_NAME))budget=true;}
    pthread_mutex_lock(&obs_lock);slot->budget=budget&&props.apiVersion>=VK_API_VERSION_1_1;
    obs_stats.vendor=props.vendorID;obs_stats.device=props.deviceID;obs_stats.api=props.apiVersion;memcpy(obs_stats.gpu,props.deviceName,sizeof(obs_stats.gpu));obs_stats.gpu[sizeof(obs_stats.gpu)-1]=0;
    pthread_mutex_unlock(&obs_lock);obs_budget(*out);return result;
}
static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL obs_instance_proc(VkInstance instance,const char *name) {
    PFN_vkVoidFunction result=obs_get_original(instance,name);if(!result)return NULL;
    if(instance){pthread_mutex_lock(&obs_lock);bool other=obs_instance&&obs_instance!=instance;if(!obs_instance)obs_instance=instance;pthread_mutex_unlock(&obs_lock);if(other)return result;}
    if(!strcmp(name,"vkGetDeviceProcAddr")){obs_get_device_original=(PFN_vkGetDeviceProcAddr)result;return(PFN_vkVoidFunction)obs_device_proc;}
    if(!strcmp(name,"vkCreateDevice")){obs_create_original=(PFN_vkCreateDevice)result;
        if(!obs_get_device_original)obs_get_device_original=(PFN_vkGetDeviceProcAddr)obs_get_original(instance,"vkGetDeviceProcAddr");
        return obs_get_device_original?(PFN_vkVoidFunction)obs_create_device:result;}
    if(!strcmp(name,"vkGetDeviceQueue")&&obs_get_device_original)return(PFN_vkVoidFunction)obs_queue;
    if(!strcmp(name,"vkGetDeviceQueue2")&&obs_get_device_original)return(PFN_vkVoidFunction)obs_queue2;
    return result;
}

static bool obs_install(void) {
    if(obs_hooked)return true;
#if defined(__aarch64__)
    void *library=dlopen("libgtav_vulkan.so",RTLD_NOW|RTLD_NOLOAD);if(!library)return false;
    unsigned char *entry=dlsym(library,"vkGetInstanceProcAddr");Dl_info info={0};
    static const unsigned char expected[16]={0xfd,0x7b,0xbd,0xa9,0xf5,0x0b,0x00,0xf9,0xf4,0x4f,0x02,0xa9,0xfd,0x03,0x00,0x91};
    if(!entry||!dladdr(entry,&info)||(uintptr_t)entry!=(uintptr_t)info.dli_fbase+0x80fa8||memcmp(entry,expected,16)){dlclose(library);return false;}
    long page=sysconf(_SC_PAGESIZE);if(page<=0||(page&(page-1))){dlclose(library);return false;}
    unsigned char *trampoline=MAP_FAILED;
    /* A nearby veneer permits an aligned, atomic single-instruction branch.
     * Concurrent callers can finish the unchanged original prologue; no thread
     * can observe a partially written four-instruction absolute entry jump. */
    uintptr_t near=(uintptr_t)entry&~((uintptr_t)page-1);
    for(unsigned n=1;n<=16&&trampoline==MAP_FAILED;++n)for(unsigned side=0;side<2&&trampoline==MAP_FAILED;++side){
        uintptr_t distance=(uintptr_t)n*4u*1024u*1024u;
        if((side==0&&near<distance)||(side==1&&near>UINTPTR_MAX-distance))continue;
        uintptr_t hint=side==0?near-distance:near+distance;
        unsigned char *memory=mmap((void *)hint,(size_t)page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
        if(memory==MAP_FAILED)continue;
        int64_t delta=(int64_t)(uintptr_t)(memory+64)-(int64_t)(uintptr_t)entry;
        if((delta&3)||delta<-(1ll<<27)||delta>=(1ll<<27))munmap(memory,(size_t)page);else trampoline=memory;
    }
    if(trampoline==MAP_FAILED){dlclose(library);return false;}
    memcpy(trampoline,expected,16);uint32_t jump[4]={0x58000050,0xd61f0200,0,0};uintptr_t back=(uintptr_t)entry+16;memcpy(jump+2,&back,8);memcpy(trampoline+16,jump,16);
    uintptr_t target=(uintptr_t)obs_instance_proc;memcpy(jump+2,&target,8);memcpy(trampoline+64,jump,16);
    __builtin___clear_cache((char *)trampoline,(char *)trampoline+80);
    if(mprotect(trampoline,(size_t)page,PROT_READ|PROT_EXEC)){munmap(trampoline,(size_t)page);dlclose(library);return false;}
    uintptr_t first=(uintptr_t)entry&~((uintptr_t)page-1);size_t length=(((uintptr_t)entry+16+page-1)&~((uintptr_t)page-1))-first;
    if(mprotect((void *)first,length,PROT_READ|PROT_WRITE|PROT_EXEC)){munmap(trampoline,(size_t)page);dlclose(library);return false;}
    obs_get_original=(PFN_vkGetInstanceProcAddr)trampoline;
    int64_t delta=(int64_t)(uintptr_t)(trampoline+64)-(int64_t)(uintptr_t)entry;
    uint32_t branch=0x14000000u|((uint32_t)(delta>>2)&0x3ffffffu);
    __atomic_store_n((uint32_t *)entry,branch,__ATOMIC_SEQ_CST);__builtin___clear_cache((char *)entry,(char *)entry+4);
    if(mprotect((void *)first,length,PROT_READ|PROT_EXEC)){uint32_t original;memcpy(&original,expected,4);__atomic_store_n((uint32_t *)entry,original,__ATOMIC_SEQ_CST);__builtin___clear_cache((char *)entry,(char *)entry+4);mprotect((void *)first,length,PROT_READ|PROT_EXEC);
        /* Keep the RX trampoline/dispatch valid for any caller that entered the
         * veneer before rollback. It is intentionally retained for process life. */
        dlclose(library);return false;}
    obs_hooked=true;dlclose(library);return true;
#else
    return false;
#endif
}
static int obs_compare_interval(const void *a,const void *b) {uint64_t x=*(const uint64_t *)a,y=*(const uint64_t *)b;return(x>y)-(x<y);}
static void obs_snapshot(char *output,size_t capacity) {
    uint64_t intervals[OBS_INTERVALS];pthread_mutex_lock(&obs_lock);struct obs_totals s=obs_stats;
    size_t count=s.interval_count<OBS_INTERVALS?(size_t)s.interval_count:OBS_INTERVALS;memcpy(intervals,obs_intervals,count*sizeof(uint64_t));pthread_mutex_unlock(&obs_lock);
    qsort(intervals,count,sizeof(uint64_t),obs_compare_interval);char gpu[768];escaped(gpu,sizeof(gpu),s.gpu);
    uint64_t p95=count?intervals[(count*95+99)/100-1]:0,p99=count?intervals[(count*99+99)/100-1]:0;
    snprintf(output,capacity,"{\"hookInstalled\":%s,\"captureActive\":%s,\"performanceHintsRequested\":%s,\"gpu\":\"%s\",\"vendorId\":%u,\"deviceId\":%u,\"apiVersion\":%u,\"trackedAllocationBytes\":%llu,\"peakTrackedAllocationBytes\":%llu,\"allocationCount\":%llu,\"freeCount\":%llu,\"allocationFailures\":%llu,\"untrackedAllocations\":%llu,\"deviceLocalBudgetBytes\":%llu,\"deviceLocalUsageBytes\":%llu,\"presentCount\":%llu,\"presentFailures\":%llu,\"deviceLossObservations\":%llu,\"surfaceErrorObservations\":%llu,\"submitCount\":%llu,\"submittedCommandBuffers\":%llu,\"graphicsPipelineRequests\":%llu,\"computePipelineRequests\":%llu,\"pipelineCreateNs\":%llu,\"swapchainCreates\":%llu,\"lastVkResult\":%d,\"presentIntervalSamples\":%llu,\"retainedIntervalSamples\":%zu,\"presentIntervalP95Ns\":%llu,\"presentIntervalP99Ns\":%llu,\"presentIntervalMaxNs\":%llu,\"scope\":\"Selected-loader device dispatch; Vulkan allocation totals exclude suballocations and pre-hook allocations. Budget/usage are driver reports, not process ownership. Intervals are presentation cadence, may include generated frames, and are not GPU or rendered-frame execution times. Percentiles cover the retained interval window. Zero budget means unavailable.\"}",
        obs_hooked?"true":"false",atomic_load(&obs_capture)?"true":"false",atomic_load(&obs_hints)?"true":"false",gpu,s.vendor,s.device,s.api,
        (unsigned long long)s.live,(unsigned long long)s.peak,(unsigned long long)s.allocations,(unsigned long long)s.frees,(unsigned long long)s.failed,(unsigned long long)s.untracked,(unsigned long long)s.heap_budget,(unsigned long long)s.heap_usage,(unsigned long long)s.presents,(unsigned long long)s.present_errors,(unsigned long long)s.device_losses,(unsigned long long)s.surface_errors,(unsigned long long)s.submits,(unsigned long long)s.command_buffers,(unsigned long long)s.graphics,(unsigned long long)s.compute,(unsigned long long)s.pipeline_ns,(unsigned long long)s.swapchains,s.last_result,(unsigned long long)s.interval_count,count,(unsigned long long)p95,(unsigned long long)p99,(unsigned long long)s.interval_max_ns);
}

static JavaVM *obs_vm;static jclass obs_hint_class;static jmethodID obs_hint_report;
static pthread_key_t obs_jni_key;static pthread_once_t obs_jni_once=PTHREAD_ONCE_INIT;
static bool obs_jni_key_valid;
static void obs_detach(void *attached){if(attached&&obs_vm)(*obs_vm)->DetachCurrentThread(obs_vm);}
static void obs_make_jni_key(void){obs_jni_key_valid=pthread_key_create(&obs_jni_key,obs_detach)==0;}
static void obs_java_hint(uint64_t work,uint64_t target){
    if(!obs_vm||!obs_hint_class||!obs_hint_report)return;
    JNIEnv *env=NULL;
    bool detach=false;int result=(*obs_vm)->GetEnv(obs_vm,(void **)&env,JNI_VERSION_1_6);
    if(result==JNI_EDETACHED){
#ifdef __ANDROID__
        if((*obs_vm)->AttachCurrentThread(obs_vm,&env,NULL)!=JNI_OK)return;
#else
        if((*obs_vm)->AttachCurrentThread(obs_vm,(void **)&env,NULL)!=JNI_OK)return;
#endif
        pthread_once(&obs_jni_once,obs_make_jni_key);detach=!obs_jni_key_valid||pthread_setspecific(obs_jni_key,(void *)1)!=0;}
    else if(result!=JNI_OK)return;
    (*env)->CallStaticVoidMethod(env,obs_hint_class,obs_hint_report,(jlong)work,(jlong)target);
    if((*env)->ExceptionCheck(env))(*env)->ExceptionClear(env);
    if(detach)(*obs_vm)->DetachCurrentThread(obs_vm);
}
JNIEXPORT jboolean JNICALL Java_com_gtavsource_android_XhynV02Native_observe(JNIEnv *env,jclass type,jboolean capture,jboolean hints,jint target){
    (void)type;
    if(!capture&&!hints){atomic_store(&obs_capture,false);atomic_store(&obs_hints,false);return JNI_TRUE;}
    if(!obs_install())return JNI_FALSE;
    if(hints&&!obs_hint_class){jclass cls=(*env)->FindClass(env,"com/gtavsource/android/XhynV02FrameHints");
        if(cls){obs_hint_class=(*env)->NewGlobalRef(env,cls);(*env)->DeleteLocalRef(env,cls);if(obs_hint_class){obs_hint_report=(*env)->GetStaticMethodID(env,obs_hint_class,"report","(JJ)V");(*env)->GetJavaVM(env,&obs_vm);}}
        if((*env)->ExceptionCheck(env))(*env)->ExceptionClear(env);}
    obs_hint_callback=obs_java_hint;atomic_store(&obs_hints,hints&&obs_hint_class&&obs_hint_report);atomic_store(&obs_target_fps,target>0&&target<=240?target:0);
    pthread_mutex_lock(&obs_lock);++obs_stats.capture_generation;pthread_mutex_unlock(&obs_lock);atomic_store(&obs_capture,capture);return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_com_gtavsource_android_XhynV02Native_capture(JNIEnv *env,jclass type,jboolean enabled){
    (void)env;(void)type;atomic_store(&obs_capture,enabled);pthread_mutex_lock(&obs_lock);++obs_stats.capture_generation;pthread_mutex_unlock(&obs_lock);
}
JNIEXPORT void JNICALL Java_com_gtavsource_android_XhynV02Native_paused(JNIEnv *env,jclass type,jboolean paused){
    (void)env;(void)type;atomic_store(&obs_paused,paused);pthread_mutex_lock(&obs_lock);for(unsigned n=0;n<OBS_QUEUES;++n)obs_queues[n].last_cpu=obs_queues[n].last_present=0;pthread_mutex_unlock(&obs_lock);
}
JNIEXPORT jstring JNICALL Java_com_gtavsource_android_XhynV02Native_metrics(JNIEnv *env,jclass type){
    (void)type;char output[4096];obs_snapshot(output,sizeof(output));return(*env)->NewStringUTF(env,output);
}
