#include <stdarg.h>
#include <stdio.h>
int __android_log_print(int p,const char*t,const char*f,...){va_list a;va_start(a,f);fprintf(stderr,"[%s] ",t);vfprintf(stderr,f,a);va_end(a);return 0;}
int __android_log_vprint(int p,const char*t,const char*f,va_list a){fprintf(stderr,"[%s] ",t);vfprintf(stderr,f,a);return 0;}
