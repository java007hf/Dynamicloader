#include <asm/ptrace.h>    
#include <sys/ptrace.h>    
#include <sys/wait.h>    
#include <sys/mman.h>    
#include <sys/stat.h>
#include "xloader.h"

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__i386__)    
#define pt_regs         user_regs_struct    
#endif

#define CPSR_T_MASK     ( 1u << 5 )    
static const char *libc_path = "/system/lib/libc.so";    
static const char *linker_path = "/system/bin/linker";    

static int ptrace_getregs(pid_t pid, struct pt_regs * regs)    
{    
    if (ptrace(PTRACE_GETREGS, pid, NULL, (void *)regs) < 0) {    
        perror("ptrace_getregs: Can not get register values");    
        return -1;    
    }    
    
    return 0;    
}    
    
static int ptrace_setregs(pid_t pid, struct pt_regs * regs)    
{    
    if (ptrace(PTRACE_SETREGS, pid, NULL, regs) < 0) {    
        perror("ptrace_setregs: Can not set register values");    
        return -1;    
    }    
    
    return 0;    
}    
    
static int ptrace_continue(pid_t pid)    
{    
    if (ptrace(PTRACE_CONT, pid, NULL, 0) < 0) {    
        perror("ptrace_cont");    
        return -1;    
    }    
    
    return 0;    
}

static int ptrace_readdata(pid_t pid,  uint8_t *src, uint8_t *buf, size_t size)    
{    
    uint32_t i, j, remain;    
    uint8_t *laddr;    
    
    union u {    
        long val;    
        char chars[sizeof(long)];    
    } d;    
    
    j = size / 4;    
    remain = size % 4;    
    
    laddr = buf;    
    
    for (i = 0; i < j; i ++) {    
        d.val = ptrace(PTRACE_PEEKTEXT, pid, src, 0);    
        memcpy(laddr, d.chars, 4);    
        src += 4;    
        laddr += 4;    
    }    
    
    if (remain > 0) {    
        d.val = ptrace(PTRACE_PEEKTEXT, pid, src, 0);    
        memcpy(laddr, d.chars, remain);    
    }    
    
    return 0;    
}    
    
static int ptrace_writedata(pid_t pid, uint8_t *dest, const uint8_t *data, size_t size)    
{    
    uint32_t i, j, remain;    
    const uint8_t *laddr;    
    
    union u {    
        long val;    
        char chars[sizeof(long)];    
    } d;    
    
    j = size / 4;    
    remain = size % 4;    
    
    laddr = data;    
    
    for (i = 0; i < j; i ++) {    
        memcpy(d.chars, laddr, 4);    
        ptrace(PTRACE_POKETEXT, pid, dest, (void *)d.val);    
    
        dest  += 4;    
        laddr += 4;    
    }    
    
    if (remain > 0) {    
        d.val = ptrace(PTRACE_PEEKTEXT, pid, dest, 0);    
        for (i = 0; i < remain; i ++) {    
            d.chars[i] = *laddr ++;    
        }    
    
        ptrace(PTRACE_POKETEXT, pid, dest, (void *)d.val);    
    }    
    
    return 0;    
}    
    
#if defined(__arm__)    
static int ptrace_call(pid_t pid, uint32_t addr, long *params, uint32_t num_params, struct pt_regs* regs)    
{    
    uint32_t i;    
    for (i = 0; i < num_params && i < 4; i ++) {    
        regs->uregs[i] = params[i];    
    }    
    
    //    
    // push remained params onto stack    
    //    
    if (i < num_params) {    
        regs->ARM_sp -= (num_params - i) * sizeof(long) ;    
        ptrace_writedata(pid, (void *)regs->ARM_sp, (uint8_t *)&params[i], (num_params - i) * sizeof(long));    
    }    
    
    regs->ARM_pc = addr;    
    if (regs->ARM_pc & 1) {    
        /* thumb */    
        regs->ARM_pc &= (~1u);    
        regs->ARM_cpsr |= CPSR_T_MASK;    
    } else {    
        /* arm */    
        regs->ARM_cpsr &= ~CPSR_T_MASK;    
    }    
    
    regs->ARM_lr = 0;        
    
    if (ptrace_setregs(pid, regs) == -1     
            || ptrace_continue(pid) == -1) {    
        printf("error\n");    
        return -1;    
    }    
    
    int stat = 0;  
    waitpid(pid, &stat, WUNTRACED);  
    while (stat != 0xb7f) {  
        if (ptrace_continue(pid) == -1) {  
            printf("error\n");  
            return -1;  
        }  
        waitpid(pid, &stat, WUNTRACED);  
    }  
    
    return 0;    
}    
    
#elif defined(__i386__)    
static long ptrace_call(pid_t pid, uint32_t addr, long *params, uint32_t num_params, struct user_regs_struct * regs)    
{    
    regs->esp -= (num_params) * sizeof(long) ;    
    ptrace_writedata(pid, (void *)regs->esp, (uint8_t *)params, (num_params) * sizeof(long));    
    
    long tmp_addr = 0x00;    
    regs->esp -= sizeof(long);    
    ptrace_writedata(pid, regs->esp, (char *)&tmp_addr, sizeof(tmp_addr));     
    
    regs->eip = addr;    
    
    if (ptrace_setregs(pid, regs) == -1     
            || ptrace_continue( pid) == -1) {    
        printf("error\n");    
        return -1;    
    }    
    
    int stat = 0;  
    waitpid(pid, &stat, WUNTRACED);  
    while (stat != 0xb7f) {  
        if (ptrace_continue(pid) == -1) {  
            printf("error\n");  
            return -1;  
        }  
        waitpid(pid, &stat, WUNTRACED);  
    }  
    
    return 0;    
}    
#else     
#error "Not supported"    
#endif    
    
static int ptrace_attach(pid_t pid)    
{    
    if (ptrace(PTRACE_ATTACH, pid, NULL, 0) < 0) {    
        perror("ptrace_attach");    
        return -1;    
    }    
    
    int status = 0;    
    waitpid(pid, &status , WUNTRACED);    
    
    return 0;    
}    
    
static int ptrace_detach(pid_t pid)    
{    
    if (ptrace(PTRACE_DETACH, pid, NULL, 0) < 0) {    
        perror("ptrace_detach");    
        return -1;    
    }    
    
    return 0;    
}    
    
static void* get_module_base(pid_t pid, const char* module_name)    
{    
    FILE *fp;    
    long addr = 0;    
    char *pch;    
    char filename[32];    
    char line[1024];    
    
    if (pid < 0) {    
        /* self process */    
        snprintf(filename, sizeof(filename), "/proc/self/maps");    
    } else {    
        snprintf(filename, sizeof(filename), "/proc/%d/maps", pid);    
    }    
    
    fp = fopen(filename, "r");    
    
    if (fp != NULL) {    
        while (fgets(line, sizeof(line), fp)) {    
            if (strstr(line, module_name)) {    
                pch = strtok( line, "-" );    
                addr = strtoul( pch, NULL, 16 );    
    
                if (addr == 0x8000)    
                    addr = 0;    
    
                break;    
            }    
        }    
    
        fclose(fp) ;    
    }    
    
    return (void *)addr;    
}    
    
static void* get_remote_addr(pid_t target_pid, const char* module_name, void* local_addr)    
{    
    void* local_handle, *remote_handle;    
    
    local_handle = get_module_base(-1, module_name);    
    remote_handle = get_module_base(target_pid, module_name);    
    
    //PRINT_LOGD("[+] get_remote_addr: local[%x], remote[%x]\n", local_handle, remote_handle);    
    
    void * ret_addr = (void *)((uint32_t)local_addr + (uint32_t)remote_handle - (uint32_t)local_handle);    
    
#if defined(__i386__)    
    if (!strcmp(module_name, libc_path)) {    
        ret_addr += 2;    
    }    
#endif    
    return ret_addr;    
}    
    
static int find_pid_of(const char *process_name)    
{    
    int id;    
    pid_t pid = -1;    
    DIR* dir;    
    FILE *fp;    
    char filename[32];    
    char cmdline[256];    
    
    struct dirent * entry;    
    
    if (process_name == NULL)    
        return -1;    
    
    dir = opendir("/proc");    
    if (dir == NULL)    
        return -1;    
    
    while((entry = readdir(dir)) != NULL) {    
        id = atoi(entry->d_name);    
        if (id != 0) {    
            sprintf(filename, "/proc/%d/cmdline", id);    
            fp = fopen(filename, "r");    
            if (fp) {    
                fgets(cmdline, sizeof(cmdline), fp);    
                fclose(fp);    
    
                if (strcmp(process_name, cmdline) == 0) {    
                    /* process found */    
                    pid = id;    
                    break;    
                }    
            }    
        }    
    }    
    
    closedir(dir);    
    return pid;    
}    
    
static long ptrace_retval(struct pt_regs * regs)    
{    
#if defined(__arm__)    
    return regs->ARM_r0;    
#elif defined(__i386__)    
    return regs->eax;    
#else    
#error "Not supported"    
#endif    
}    
    
static long ptrace_ip(struct pt_regs * regs)    
{    
#if defined(__arm__)    
    return regs->ARM_pc;    
#elif defined(__i386__)    
    return regs->eip;    
#else    
#error "Not supported"    
#endif    
}    
    
static int ptrace_call2(pid_t target_pid, const char * func_name, void * func_addr, long * parameters, int param_num, struct pt_regs * regs)     
{    
    //PRINT_LOGD("Calling %s in target process.\n", func_name);    
    if (ptrace_call(target_pid, (uint32_t)func_addr, parameters, param_num, regs) == -1)    
        return -1;    
    
    if (ptrace_getregs(target_pid, regs) == -1)    
        return -1;    
    //PRINT_LOGD("Target process returned from %s, return value=%x, pc=%x \n",     
    //        func_name, ptrace_retval(regs), ptrace_ip(regs));    
    PRINT_LOGD("value=%x, pc=%x \n", ptrace_retval(regs), ptrace_ip(regs));    
    return 0;    
}    
    
static int trace_remote_process(pid_t target_pid, const char *library_path, 
                 const char *function_name, const void *param, size_t param_size)    
{    
    int ret = SUCCESS;
	void *perr = NULL;
	char perrbuf[128] = {0};
    struct pt_regs regs, original_regs;    
    void *mmap_addr, *dlopen_addr, *dlsym_addr, *dlclose_addr, *dlerror_addr;    
    long parameters[10];    
    uint8_t *map_base = 0;  

	//forbid reload
    PRINT_LOGD("Process: %d\n", target_pid); 
    void* remote_library = get_module_base(target_pid, library_path);    
    if (remote_library){
        PRINT_LOGD("Library exist : %x\n", remote_library);
		ret = ERROR_XLOADER_PROCESS_LOADED;
        goto exit;
    }
	
    //attach
    if (ptrace_attach(target_pid) == -1){
		ret = -ERROR_XLOADER_PTRACE_FAILED;
        goto exit;
	}
    if (ptrace_getregs(target_pid, &regs) == -1){
		ret = -ERROR_XLOADER_PTRACE_FAILED;
        goto exit_detach;    
	}
    memcpy(&original_regs, &regs, sizeof(regs));    
   
    //mmap
    mmap_addr = get_remote_addr(target_pid, libc_path, (void *)mmap);    
    PRINT_LOGD("%x\n", mmap_addr);    
    parameters[0] = 0;  //addr    
    parameters[1] = 0x4000; //size    
    parameters[2] = PROT_READ | PROT_WRITE | PROT_EXEC;  //prot    
    parameters[3] =  MAP_ANONYMOUS | MAP_PRIVATE; //flags    
    parameters[4] = 0; //fd    
    parameters[5] = 0; //offset    
    if (ptrace_call2(target_pid, "mmap", mmap_addr, parameters, 6, &regs) == -1){
		ret = -ERROR_XLOADER_MMAP_FAILED;
        goto exit_regs; 
	}
    map_base = (uint8_t *)ptrace_retval(&regs);    
    PRINT_LOGD("Base address: %x\n", map_base);
	
    dlopen_addr = get_remote_addr( target_pid, linker_path, (void *)dlopen );    
    dlsym_addr = get_remote_addr( target_pid, linker_path, (void *)dlsym );    
    dlclose_addr = get_remote_addr( target_pid, linker_path, (void *)dlclose );    
    dlerror_addr = get_remote_addr( target_pid, linker_path, (void *)dlerror );    
    PRINT_LOGD("%x, %x, %x, %x\n", dlopen_addr, dlsym_addr, dlclose_addr, dlerror_addr);
	if(dlopen_addr == NULL  || dlsym_addr == NULL 
			|| dlclose_addr == NULL || dlerror_addr == NULL){
		ret = -ERROR_XLOADER_DL_NOT_LOADED;
        goto exit_regs; 				
	}
	
    //dlopen
	//PRINT_LOGD("open %s\n", library_path);
	ptrace_writedata(target_pid, map_base, (uint8_t *)library_path, strlen(library_path) + 1);    
	parameters[0] = (long)map_base;
	parameters[1] = RTLD_NOW| RTLD_GLOBAL;
	if (ptrace_call2(target_pid, "dlopen", dlopen_addr, parameters, 2, &regs) == -1){
		PRINT_LOGD("%s open failed\n", library_path);
		ret = -ERROR_XLOADER_REMOTE_CALL_FAILED;
		goto exit_regs;    
	}
	void *sohandle = (void *)ptrace_retval(&regs);
#ifdef DEBUG
	if (sohandle == NULL){
		if (ptrace_call2(target_pid, "dlerror", dlerror_addr, parameters, 0, &regs) == -1){
			PRINT_LOGD("%s dlerror failed\n", library_path);
			ret = -ERROR_XLOADER_DLERROR_FAILED;
			goto exit_regs;    
		}
		perr = (void *)ptrace_retval(&regs);
		memset(perrbuf, 0, sizeof(perrbuf));
		ptrace_readdata(target_pid, perr, (uint8_t *)perrbuf, sizeof(perrbuf) - 1);
		PRINT_LOGD("open null : %s\n", perrbuf);
		ret = -ERROR_XLOADER_DLOPEN_FAILED;
		goto exit_regs;
	}
#endif
    
    //dlsym
#define FUNCTION_NAME_ADDR_OFFSET       0x100    
    ptrace_writedata(target_pid, map_base + FUNCTION_NAME_ADDR_OFFSET, (uint8_t *)function_name, strlen(function_name) + 1);    
    parameters[0] = (long)sohandle;       
    parameters[1] = (long)map_base + FUNCTION_NAME_ADDR_OFFSET;     
    if (ptrace_call2(target_pid, "dlsym", dlsym_addr, parameters, 2, &regs) == -1){
        PRINT_LOGD("%s not found\n", function_name);
		ret = -ERROR_XLOADER_REMOTE_CALL_FAILED;
        goto exit_regs;    
	}    
    void * client_entry_addr = (void *)ptrace_retval(&regs);    
    PRINT_LOGD("client:%p\n", client_entry_addr);
    if(client_entry_addr == NULL){
		if (ptrace_call2(target_pid, "dlerror", dlerror_addr, parameters, 0, &regs) == -1){
			PRINT_LOGD("%s dlerror failed\n", library_path);
			ret = -ERROR_XLOADER_DLERROR_FAILED;
			goto exit_regs;    
		}
		perr = (void *)ptrace_retval(&regs);
		memset(perrbuf, 0, sizeof(perrbuf));
		ptrace_readdata(target_pid, perr, (uint8_t *)perrbuf, sizeof(perrbuf) - 1);
        PRINT_LOGD("open null : %s\n", perrbuf);
		ret = -ERROR_XLOADER_DLSYM_FAILED;
        goto exit_regs;
    }	
    
    //call fun
#define FUNCTION_PARAM_ADDR_OFFSET      0x200    
    ptrace_writedata(target_pid, map_base + FUNCTION_PARAM_ADDR_OFFSET, (uint8_t *)param, param_size);    
    parameters[0] = (long)map_base + FUNCTION_PARAM_ADDR_OFFSET;      
    if (ptrace_call2(target_pid, function_name, client_entry_addr, parameters, 1, &regs) == -1){  
		ret = -ERROR_XLOADER_REMOTE_CALL_FAILED;
        goto exit_regs;        
    }
	ret = (int)ptrace_retval(&regs);
	
    //dlclose
    parameters[0] = (long)sohandle;       
    if (ptrace_call2(target_pid, "dlclose", dlclose, parameters, 1, &regs) == -1){
		ret = -ERROR_XLOADER_REMOTE_CALL_FAILED;
        goto exit_regs;    
    }
	
    //detach     
exit_regs:    
    ptrace_setregs(target_pid, &original_regs);    
exit_detach:
    ptrace_detach(target_pid);    
exit:
    return ret;
}    

int xload(const char *packageName, const char *processName, const char *modulePath)
{
    PRINT_LOGD("xload: %s, %s, %s\n", packageName, processName, modulePath);
    PRINT_LOGD("xload: %s, %s, %s\n", CLIENT_PATH_CURRENT, BRIDGE_PATH_CURRENT, BRIDGE_LIB_PATH_CURRENT);
				
    struct stat st;
	if ( stat(CLIENT_PATH_CURRENT, &st) < 0 ){
        PRINT_LOGD("client not found!!");
        return -ERROR_XLOADER_CLIENT_NOT_FOUND;
    }
    if ( stat(BRIDGE_PATH_CURRENT, &st) < 0 ){
        PRINT_LOGD("bridge not found!!");
        return -ERROR_XLOADER_BRIDEG_NOT_FOUND;
    }
	if ( stat(BRIDGE_LIB_PATH_CURRENT, &st) < 0 ){
        PRINT_LOGD("bridge lib not found!!");
        return -ERROR_XLOADER_BRIDEG_NOT_FOUND;
    }
    if ( stat(modulePath, &st) < 0 ){
        PRINT_LOGD("module not found!!");
        return -ERROR_XLOADER_MODULE_NOT_FOUND;
    }
    
	
    pid_t target_pid = find_pid_of(processName != NULL ? processName : packageName);    
    if (-1 == target_pid) {  
        PRINT_LOGD("process not found");
        return ERROR_XLOADER_PROCESS_NOT_FOUND;
    }
    
    XloaderInfo *pXloaderInfo = (XloaderInfo *)malloc(sizeof(XloaderInfo));
    memset(pXloaderInfo, 0, sizeof(XloaderInfo));
    strncpy(pXloaderInfo->packageName, packageName, STR_LEN_LIMIT);
    strncpy(pXloaderInfo->bridgePath, BRIDGE_PATH_CURRENT, STR_LEN_LIMIT);
    strncpy(pXloaderInfo->modulePath, modulePath, STR_LEN_LIMIT);

    int ret = trace_remote_process(target_pid, CLIENT_PATH_CURRENT, 
                CLIENT_ENTRY_FUNC, (void *)pXloaderInfo, sizeof(XloaderInfo));   
	
	free(pXloaderInfo);
	return ret;
}

#ifdef XLOADER_BIN

static int usage()
{
    PRINT_ERROR("usage: xloader [OPTIONS] <package_name> <module_path>\n"
          "    -p: specify process name to trace\n"
          "\n");
    return 1;
}

int main(int argc, char** argv) {    
    char *package_name = NULL;
    char *process_name = NULL;
    char *module_path = NULL;
    
    int opt;
    while ((opt = getopt(argc, argv, "p:")) != -1) {
        switch (opt) {
            case 'p':
                process_name = optarg;
                break;
            case '?':
            default:
                return usage();
        }
    }

    int i;
    for (i = optind; i < argc; i++) {
        char* arg = argv[i];
        if (!package_name) {
            package_name = arg;
        } else if (!module_path) {
            module_path = arg;
        } else {
            PRINT_ERROR("too many arguments\n");
            return usage();
        }
    }

    if (package_name == NULL) {
        PRINT_ERROR("no package name specified\n");
        return usage();
    }

    if (module_path == NULL) {
        PRINT_ERROR("no module path specified\n");
        return usage();
    }
 
   return xload(package_name, process_name, module_path);
}
#endif

#ifdef __cplusplus
}
#endif
