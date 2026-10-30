/* Real observer dispatch exercised with explicit Vulkan mocks, not a GPU test. */
static atomic_uint om_memory=100;
static unsigned om_device=1,om_hint_calls;
static atomic_uint om_calls,om_free_calls;
static VkResult om_result=VK_SUCCESS;
static const VkAllocationCallbacks *om_allocator;
static VKAPI_ATTR VkResult VKAPI_CALL om_alloc(VkDevice d,const VkMemoryAllocateInfo *i,const VkAllocationCallbacks *a,VkDeviceMemory *m){
    assert(d==(VkDevice)(uintptr_t)1||d==(VkDevice)(uintptr_t)2);assert(i->sType==VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);assert(a==om_allocator);++om_calls;
    if(om_result==VK_SUCCESS)*m=(VkDeviceMemory)(uintptr_t)atomic_fetch_add(&om_memory,1);
    return om_result;
}
static VKAPI_ATTR VkResult VKAPI_CALL om_alloc2(VkDevice d,const VkMemoryAllocateInfo *i,const VkAllocationCallbacks *a,VkDeviceMemory *m){assert(d==(VkDevice)(uintptr_t)2);return om_alloc(d,i,a,m);}
static VKAPI_ATTR void VKAPI_CALL om_free(VkDevice d,VkDeviceMemory m,const VkAllocationCallbacks *a){(void)d;(void)m;assert(a==om_allocator);++om_free_calls;}
static VKAPI_ATTR void VKAPI_CALL om_queue(VkDevice d,uint32_t f,uint32_t i,VkQueue *q){assert(f==3&&i==1);*q=(VkQueue)((uintptr_t)d+1000);}
static VKAPI_ATTR void VKAPI_CALL om_queue2(VkDevice d,const VkDeviceQueueInfo2 *i,VkQueue *q){assert(i->queueFamilyIndex==3);*q=(VkQueue)((uintptr_t)d+1000);}
static VKAPI_ATTR VkResult VKAPI_CALL om_present(VkQueue q,const VkPresentInfoKHR *i){assert((uintptr_t)q>=1001&&i->swapchainCount==1);return om_result;}
static VKAPI_ATTR VkResult VKAPI_CALL om_submit(VkQueue q,uint32_t n,const VkSubmitInfo *i,VkFence f){(void)q;assert(n==1&&i->commandBufferCount==2&&f==(VkFence)(uintptr_t)55);return om_result;}
static VKAPI_ATTR VkResult VKAPI_CALL om_submit2(VkQueue q,uint32_t n,const VkSubmitInfo2 *i,VkFence f){(void)q;assert(n==1&&i->commandBufferInfoCount==3&&f==(VkFence)(uintptr_t)55);return om_result;}
static VKAPI_ATTR VkResult VKAPI_CALL om_graphics(VkDevice d,VkPipelineCache c,uint32_t n,const VkGraphicsPipelineCreateInfo *i,const VkAllocationCallbacks *a,VkPipeline *p){(void)d;(void)i;(void)p;assert(c==(VkPipelineCache)(uintptr_t)99&&n==2&&a==om_allocator);return om_result;}
static VKAPI_ATTR VkResult VKAPI_CALL om_compute(VkDevice d,VkPipelineCache c,uint32_t n,const VkComputePipelineCreateInfo *i,const VkAllocationCallbacks *a,VkPipeline *p){(void)d;(void)i;(void)p;assert(c==(VkPipelineCache)(uintptr_t)99&&n==3&&a==om_allocator);return om_result;}
static VKAPI_ATTR VkResult VKAPI_CALL om_swap(VkDevice d,const VkSwapchainCreateInfoKHR *i,const VkAllocationCallbacks *a,VkSwapchainKHR *s){(void)d;assert(i->imageExtent.width==1280&&a==om_allocator);*s=(VkSwapchainKHR)(uintptr_t)99;return om_result;}
static VKAPI_ATTR void VKAPI_CALL om_destroy_swap(VkDevice d,VkSwapchainKHR s,const VkAllocationCallbacks *a){(void)d;assert(s==(VkSwapchainKHR)(uintptr_t)99&&a==om_allocator);}
static VKAPI_ATTR void VKAPI_CALL om_destroy(VkDevice d,const VkAllocationCallbacks *a){(void)d;assert(a==om_allocator);}
static PFN_vkVoidFunction om_device_proc(VkDevice d,const char *name){
#define OM(n,f) if(!strcmp(name,#n))return(PFN_vkVoidFunction)f
    if(!strcmp(name,"vkAllocateMemory"))return(PFN_vkVoidFunction)(d==(VkDevice)(uintptr_t)2?om_alloc2:om_alloc);
    OM(vkFreeMemory,om_free);OM(vkGetDeviceQueue,om_queue);OM(vkGetDeviceQueue2,om_queue2);OM(vkQueuePresentKHR,om_present);OM(vkQueueSubmit,om_submit);OM(vkQueueSubmit2,om_submit2);OM(vkQueueSubmit2KHR,om_submit2);
    OM(vkCreateGraphicsPipelines,om_graphics);OM(vkCreateComputePipelines,om_compute);OM(vkCreateSwapchainKHR,om_swap);OM(vkDestroySwapchainKHR,om_destroy_swap);OM(vkDestroyDevice,om_destroy);
    if(!strcmp(name,"vkUnknownPassthrough"))return unused;
    return NULL;
}
static VKAPI_ATTR VkResult VKAPI_CALL om_create(VkPhysicalDevice p,const VkDeviceCreateInfo *i,const VkAllocationCallbacks *a,VkDevice *d){(void)p;assert(i->enabledExtensionCount==0&&a==om_allocator);if(om_result==VK_SUCCESS)*d=(VkDevice)(uintptr_t)om_device++;return om_result;}
static void om_properties(VkPhysicalDevice p,VkPhysicalDeviceProperties *v){(void)p;memset(v,0,sizeof(*v));v->vendorID=0x5143;v->apiVersion=VK_API_VERSION_1_1;strcpy(v->deviceName,"Mock Adreno");}
static VkResult om_extensions(VkPhysicalDevice p,const char *l,uint32_t *n,VkExtensionProperties *e){(void)p;assert(!l);*n=1;if(e)strcpy(e[0].extensionName,VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);return VK_SUCCESS;}
static void om_budget(VkPhysicalDevice p,VkPhysicalDeviceMemoryProperties2 *v){(void)p;v->memoryProperties.memoryHeapCount=1;v->memoryProperties.memoryHeaps[0].flags=VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;VkPhysicalDeviceMemoryBudgetPropertiesEXT *b=v->pNext;b->heapBudget[0]=4000000000ull;b->heapUsage[0]=2000000000ull;}
static PFN_vkVoidFunction om_instance(VkInstance i,const char *n){(void)i;
#define OMI(s,f) if(!strcmp(n,#s))return(PFN_vkVoidFunction)f
    OMI(vkCreateDevice,om_create);OMI(vkGetDeviceProcAddr,om_device_proc);OMI(vkGetPhysicalDeviceProperties,om_properties);OMI(vkEnumerateDeviceExtensionProperties,om_extensions);OMI(vkGetPhysicalDeviceMemoryProperties2,om_budget);return NULL;
}
static void om_hint(uint64_t work,uint64_t target){assert(work>0&&work<1000000000ull&&target==16666666);++om_hint_calls;}
struct om_work {VkDevice device;PFN_vkAllocateMemory allocate;PFN_vkFreeMemory release;};
static void *om_allocations_thread(void *data){
    struct om_work *work=data;VkMemoryAllocateInfo info={.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,.allocationSize=256};
    for(unsigned n=0;n<1000;n++){VkDeviceMemory memory;assert(work->allocate(work->device,&info,om_allocator,&memory)==VK_SUCCESS);work->release(work->device,memory,om_allocator);}
    return NULL;
}
static int observer_checks(const char *directory){
    int checks=0;VkAllocationCallbacks callbacks={.pUserData=(void *)(uintptr_t)1234};om_allocator=&callbacks;
    obs_get_original=om_instance;obs_instance=VK_NULL_HANDLE;obs_hooked=true;
    VkInstance instance=(VkInstance)(uintptr_t)100;VkPhysicalDevice physical=(VkPhysicalDevice)(uintptr_t)200;
    PFN_vkCreateDevice create=(PFN_vkCreateDevice)obs_instance_proc(instance,"vkCreateDevice");assert(create==obs_create_device);++checks;
    PFN_vkGetDeviceProcAddr get=(PFN_vkGetDeviceProcAddr)obs_instance_proc(instance,"vkGetDeviceProcAddr");
    VkDeviceCreateInfo ci={.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};VkDevice a,b;assert(create(physical,&ci,om_allocator,&a)==VK_SUCCESS);assert(create(physical,&ci,om_allocator,&b)==VK_SUCCESS);checks+=2;
    assert(obs_instance_proc((VkInstance)(uintptr_t)101,"vkCreateDevice")== (PFN_vkVoidFunction)om_create);++checks;
    assert(!get(a,"vkNotAvailable"));assert(get(a,"vkUnknownPassthrough")==unused);assert(get((VkDevice)(uintptr_t)9,"vkAllocateMemory")== (PFN_vkVoidFunction)om_alloc);checks+=3;
    VkMemoryAllocateInfo ai={.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,.allocationSize=4096};VkDeviceMemory ma,mb;
    PFN_vkAllocateMemory alloc_a=(PFN_vkAllocateMemory)get(a,"vkAllocateMemory"),alloc_b=(PFN_vkAllocateMemory)get(b,"vkAllocateMemory");
    assert(alloc_a(a,&ai,om_allocator,&ma)==VK_SUCCESS);assert(alloc_b(b,&ai,om_allocator,&mb)==VK_SUCCESS);assert(obs_stats.live==8192&&obs_stats.peak==8192);checks+=3;
    om_result=VK_ERROR_OUT_OF_DEVICE_MEMORY;VkDeviceMemory unchanged=(VkDeviceMemory)(uintptr_t)123;assert(alloc_a(a,&ai,om_allocator,&unchanged)==om_result);assert(unchanged==(VkDeviceMemory)(uintptr_t)123&&obs_stats.live==8192&&obs_stats.failed==1);checks+=2;om_result=VK_SUCCESS;
    PFN_vkFreeMemory free_a=(PFN_vkFreeMemory)get(a,"vkFreeMemory");free_a(a,ma,om_allocator);free_a(a,VK_NULL_HANDLE,om_allocator);assert(obs_stats.live==4096&&om_free_calls==2);++checks;
    VkQueue qa,qb;((PFN_vkGetDeviceQueue)get(a,"vkGetDeviceQueue"))(a,3,1,&qa);
    VkDeviceQueueInfo2 qi={.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_INFO_2,.queueFamilyIndex=3};((PFN_vkGetDeviceQueue2)get(b,"vkGetDeviceQueue2"))(b,&qi,&qb);
    VkPresentInfoKHR pi={.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,.swapchainCount=1};PFN_vkQueuePresentKHR present_a=(PFN_vkQueuePresentKHR)get(a,"vkQueuePresentKHR");
    atomic_store(&obs_capture,true);atomic_store(&obs_hints,true);atomic_store(&obs_target_fps,60);obs_hint_callback=om_hint;
    assert(present_a(qa,&pi)==VK_SUCCESS);for(volatile int n=0;n<10000;++n){}assert(present_a(qa,&pi)==VK_SUCCESS);assert(obs_stats.interval_count==1&&om_hint_calls==1);checks+=3;
    Java_com_gtavsource_android_XhynV02Native_paused(&environment,NULL,JNI_TRUE);uint64_t recorded=obs_stats.interval_count;present_a(qa,&pi);assert(obs_stats.interval_count==recorded);++checks;
    Java_com_gtavsource_android_XhynV02Native_paused(&environment,NULL,JNI_FALSE);present_a(qa,&pi);assert(obs_stats.interval_count==recorded);++checks;
    om_result=VK_ERROR_DEVICE_LOST;assert(present_a(qa,&pi)==om_result);assert(obs_stats.device_losses==1&&obs_stats.present_errors==1);checks+=2;om_result=VK_SUCCESS;
    VkSubmitInfo submit={.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO,.commandBufferCount=2};assert(((PFN_vkQueueSubmit)get(a,"vkQueueSubmit"))(qa,1,&submit,(VkFence)(uintptr_t)55)==VK_SUCCESS);
    VkSubmitInfo2 submit2={.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO_2,.commandBufferInfoCount=3};assert(((PFN_vkQueueSubmit2)get(b,"vkQueueSubmit2KHR"))(qb,1,&submit2,(VkFence)(uintptr_t)55)==VK_SUCCESS);assert(obs_stats.submits==2&&obs_stats.command_buffers==5);checks+=3;
    assert(((PFN_vkCreateGraphicsPipelines)get(a,"vkCreateGraphicsPipelines"))(a,(VkPipelineCache)(uintptr_t)99,2,NULL,om_allocator,NULL)==VK_SUCCESS);
    assert(((PFN_vkCreateComputePipelines)get(b,"vkCreateComputePipelines"))(b,(VkPipelineCache)(uintptr_t)99,3,NULL,om_allocator,NULL)==VK_SUCCESS);assert(obs_stats.graphics==2&&obs_stats.compute==3);checks+=3;
    VkSwapchainCreateInfoKHR si={.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,.imageExtent={1280,720}};VkSwapchainKHR swap;
    assert(((PFN_vkCreateSwapchainKHR)get(a,"vkCreateSwapchainKHR"))(a,&si,om_allocator,&swap)==VK_SUCCESS);((PFN_vkDestroySwapchainKHR)get(a,"vkDestroySwapchainKHR"))(a,swap,om_allocator);assert(obs_stats.swapchains==1&&obs_queues[0].last_present==0);checks+=2;
    char report[4096];obs_snapshot(report,sizeof(report));assert(strstr(report,"\"deviceLocalBudgetBytes\":4000000000")&&strstr(report,"\"deviceLocalUsageBytes\":2000000000")&&strstr(report,"\"allocationFailures\":1"));++checks;
    char path[PATH_MAX];snprintf(path,sizeof(path),"%s/observer-metrics.json",directory);FILE *file=fopen(path,"wb");assert(file);fwrite(report,1,strlen(report),file);fclose(file);
    struct om_work work={a,alloc_a,free_a};pthread_t workers[8];
    for(unsigned n=0;n<8;n++)assert(!pthread_create(&workers[n],NULL,om_allocations_thread,&work));
    for(unsigned n=0;n<8;n++)assert(!pthread_join(workers[n],NULL));
    assert(obs_stats.live==4096&&obs_stats.allocations==8002&&obs_stats.frees==8001);++checks;
    ((PFN_vkDestroyDevice)get(a,"vkDestroyDevice"))(a,om_allocator);((PFN_vkDestroyDevice)get(b,"vkDestroyDevice"))(b,om_allocator);assert(obs_stats.live==0);++checks;
    Java_com_gtavsource_android_XhynV02Native_capture(&environment,NULL,JNI_FALSE);assert(!atomic_load(&obs_capture));++checks;
    return checks;
}
