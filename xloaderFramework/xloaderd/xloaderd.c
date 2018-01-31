/*
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#include <sys/capability.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <linux/prctl.h>
#include "xloaderd.h"

#define BUFFER_MAX    1024  /* input buffer for commands */
#define TOKEN_MAX     8     /* max number of arguments in buffer */
#define REPLY_MAX     256   /* largest reply allowed */

static int do_ping(char **arg, char reply[REPLY_MAX])
{
    return 0;
}

static int do_xload(char **arg, char reply[REPLY_MAX])
{
    return xload(arg[0], arg[1], arg[2]);
}

struct cmdinfo {
    const char *name;
    unsigned numargs;
    int (*func)(char **arg, char reply[REPLY_MAX]);
};

struct cmdinfo cmds[] = {
    { "ping",                 0, do_ping },
    { "xload",                 3, do_xload },
};

static int readx(int s, void *_buf, int count)
{
    char *buf = _buf;
    int n = 0, r;
    if (count < 0) return -1;
    while (n < count) {
        r = read(s, buf + n, count - n);
        if (r < 0) {
            if (errno == EINTR) continue;
            ALOGE("read error: %s\n", strerror(errno));
            return -1;
        }
        if (r == 0) {
            ALOGE("eof\n");
            return -1; /* EOF */
        }
        n += r;
    }
    return 0;
}

static int writex(int s, const void *_buf, int count)
{
    const char *buf = _buf;
    int n = 0, r;
    if (count < 0) return -1;
    while (n < count) {
        r = write(s, buf + n, count - n);
        if (r < 0) {
            if (errno == EINTR) continue;
            ALOGE("write error: %s\n", strerror(errno));
            return -1;
        }
        n += r;
    }
    return 0;
}


/* Tokenize the command buffer, locate a matching command,
 * ensure that the required number of arguments are provided,
 * call the function(), return the result.
 */
static int execute(int s, char cmd[BUFFER_MAX])
{
    char reply[REPLY_MAX];
    char *arg[TOKEN_MAX+1];
    unsigned i;
    unsigned n = 0;
    unsigned short count;
    int ret = -1;

    // ALOGI("execute('%s')\n", cmd);

        /* default reply is "" */
    reply[0] = 0;

        /* n is number of args (not counting arg[0]) */
    arg[0] = cmd;
    while (*cmd) {
        if (isspace(*cmd)) {
            *cmd++ = 0;
            n++;
            arg[n] = cmd;
            if (n == TOKEN_MAX) {
                ALOGE("too many arguments\n");
                goto done;
            }
        }
        cmd++;
    }

    for (i = 0; i < sizeof(cmds) / sizeof(cmds[0]); i++) {
        if (!strcmp(cmds[i].name,arg[0])) {
            if (n != cmds[i].numargs) {
                ALOGE("%s requires %d arguments (%d given)\n",
                     cmds[i].name, cmds[i].numargs, n);
            } else {
                ret = cmds[i].func(arg + 1, reply);
            }
            goto done;
        }
    }
    ALOGE("unsupported command '%s'\n", arg[0]);

done:
    if (reply[0]) {
        n = snprintf(cmd, BUFFER_MAX, "%d %s", ret, reply);
    } else {
        n = snprintf(cmd, BUFFER_MAX, "%d", ret);
    }
    if (n > BUFFER_MAX) n = BUFFER_MAX;
    count = n;

    // ALOGI("reply: '%s'\n", cmd);
    if (writex(s, &count, sizeof(count))) return -1;
    if (writex(s, cmd, count)) return -1;
    return 0;
}

#ifdef XLOADERD_SOCKET_PRIVATE

#define AM_PATH "/system/bin/app_process", "/system/bin", "com.android.commands.am.Am"
#define ACTION_BOARDCAST "broadcast", "-n", JAVA_PACKAGE_NAME "/" JAVA_PACKAGE_NAME ".xloaderReceiver"

static char g_sock_path[PATH_MAX] = {0};

void set_identity(unsigned int uid) {
    /*
     * Set effective uid back to root, otherwise setres[ug]id will fail
     * if uid isn't root.
     */
    if (seteuid(0)) {
        exit(EXIT_FAILURE);
    }
    if (setresgid(uid, uid, uid)) {
        exit(EXIT_FAILURE);
    }
    if (setresuid(uid, uid, uid)) {
        exit(EXIT_FAILURE);
    }
}

int silent_run(char* const args[]) {
    set_identity(0);
    pid_t pid;
    pid = fork();
    /* Parent */
    if (pid < 0) {
        return -1;
    }
    else if (pid > 0) {
        return 0;
    }
    int zero = open("/dev/zero", O_RDONLY | O_CLOEXEC);
    dup2(zero, 0);
    int null = open("/dev/null", O_WRONLY | O_CLOEXEC);
    dup2(null, 1);
    dup2(null, 2);
    setenv("CLASSPATH", "/system/framework/am.jar", 1);
    execv(args[0], args);
    _exit(EXIT_FAILURE);
    return -1;
}

int boardcast_socket(char* sock_path) {
    char user[64];
    int ret;
    char *request_command[] = {
        AM_PATH,
        ACTION_BOARDCAST,
        "--es",
        "socket",
        sock_path,
        NULL
    };

    return silent_run(request_command);
}

static int socket_create_temp(char *path, size_t len) {
    int fd;
    struct sockaddr_un sun;

    fd = socket(AF_LOCAL, SOCK_STREAM, 0);
    if (fd < 0) {
        return -1;
    }
    if (fcntl(fd, F_SETFD, FD_CLOEXEC)) {
        goto err;
    }

    memset(&sun, 0, sizeof(sun));
    sun.sun_family = AF_LOCAL;
    snprintf(path, len, "/dev/socket/.socket%d", getpid());
    memset(sun.sun_path, 0, sizeof(sun.sun_path));
    snprintf(sun.sun_path, sizeof(sun.sun_path), "%s", path);

    /*
     * Delete the socket to protect from situations when
     * something bad occured previously and the kernel reused pid from that process.
     * Small probability, isn't it.
     */
    unlink(sun.sun_path);

    if (bind(fd, (struct sockaddr*)&sun, sizeof(sun)) < 0) {
        goto err;
    }

    return fd;
err:
    close(fd);
    return -1;
}

static void cleanup_signal(int sig) {
    if (g_sock_path[0]) {
        if (unlink(g_sock_path))
            ALOGE("unlink (%s)", g_sock_path);
        g_sock_path[0] = 0;
    }
    exit(128 + sig);
}

static int create_and_boardcast_socket()
{
    int socket_serv_fd = socket_create_temp(g_sock_path, sizeof(g_sock_path));
    if (socket_serv_fd < 0) {
		ALOGE("create socket failed\n");
    }

    if (boardcast_socket(g_sock_path) < 0) {
		ALOGE("boardcast socket failed\n");
    }
	
	return socket_serv_fd;
}
#endif


#ifndef XLOADER_BIN
int main(const int argc, const char *argv[]) {
    char buf[BUFFER_MAX];
    struct sockaddr addr;
    socklen_t alen;
    int lsocket, s, count;

#ifdef XLOADERD_SOCKET_PRIVATE
	lsocket = create_and_boardcast_socket();
	signal(SIGHUP, cleanup_signal);
    signal(SIGPIPE, cleanup_signal);
    signal(SIGTERM, cleanup_signal);
    signal(SIGQUIT, cleanup_signal);
    signal(SIGINT, cleanup_signal);
    signal(SIGABRT, cleanup_signal);
#else
	lsocket = android_get_control_socket(SOCKET_PATH);
#endif

    if (lsocket < 0) {
        ALOGE("Failed to get socket from environment: %s\n", strerror(errno));
        exit(1);
    }
    if (listen(lsocket, 5)) {
        ALOGE("Listen on socket failed: %s\n", strerror(errno));
        exit(1);
    }
    fcntl(lsocket, F_SETFD, FD_CLOEXEC);

    for (;;) {
        alen = sizeof(addr);
        s = accept(lsocket, &addr, &alen);
        if (s < 0) {
            ALOGE("Accept failed: %s\n", strerror(errno));
            continue;
        }
        fcntl(s, F_SETFD, FD_CLOEXEC);

        ALOGI("new connection\n");
        for (;;) {
            unsigned short count;
            if (readx(s, &count, sizeof(count))) {
                ALOGE("failed to read size\n");
                break;
            }
            if ((count < 1) || (count >= BUFFER_MAX)) {
                ALOGE("invalid size %d\n", count);
                break;
            }
            if (readx(s, buf, count)) {
                ALOGE("failed to read command\n");
                break;
            }
            buf[count] = 0;
            if (execute(s, buf)) break;
        }
        ALOGI("closing connection\n");
        close(s);
    }

    return 0;
}
#endif
