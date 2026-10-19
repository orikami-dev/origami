#include <string.h>
#include "libretro.h"
static retro_environment_t env; static retro_video_refresh_t video;
static struct retro_hw_render_callback hw;
static void (*ClearColor)(float,float,float,float); static void (*Clear)(unsigned);
static void (*BindFramebuffer)(unsigned,unsigned); static void (*Viewport)(int,int,int,int);
static void (*Enable)(unsigned); static void (*Disable)(unsigned); static void (*Scissor)(int,int,int,int);
int resets;
static void reset(void){ resets++;
 ClearColor=(void*)hw.get_proc_address("glClearColor"); Clear=(void*)hw.get_proc_address("glClear");
 BindFramebuffer=(void*)hw.get_proc_address("glBindFramebuffer"); Viewport=(void*)hw.get_proc_address("glViewport");
 Enable=(void*)hw.get_proc_address("glEnable"); Disable=(void*)hw.get_proc_address("glDisable"); Scissor=(void*)hw.get_proc_address("glScissor");}
static void destroy(void){}
void retro_set_environment(retro_environment_t e){env=e;}
void retro_set_video_refresh(retro_video_refresh_t v){video=v;}
void retro_set_audio_sample(retro_audio_sample_t a){} void retro_set_audio_sample_batch(retro_audio_sample_batch_t a){}
void retro_set_input_poll(retro_input_poll_t a){} void retro_set_input_state(retro_input_state_t a){}
void retro_set_controller_port_device(unsigned a,unsigned b){}
void retro_init(void){} void retro_deinit(void){} unsigned retro_api_version(void){return 1;}
void retro_get_system_info(struct retro_system_info *i){memset(i,0,sizeof *i);i->library_name="glcore";i->library_version="1";i->valid_extensions="bin";}
void retro_get_system_av_info(struct retro_system_av_info *a){memset(a,0,sizeof *a);a->geometry.base_width=64;a->geometry.base_height=32;a->geometry.max_width=64;a->geometry.max_height=32;a->timing.fps=60;a->timing.sample_rate=44100;}
void retro_reset(void){}
void retro_run(void){
 BindFramebuffer(0x8D40,(unsigned)hw.get_current_framebuffer()); Viewport(0,0,64,32);
 Disable(0x0C11); ClearColor(0,0,1,1); Clear(0x4000|0x100);
 Enable(0x0C11); Scissor(0,16,64,16); ClearColor(1,0,0,1); Clear(0x4000); Disable(0x0C11);
 video(RETRO_HW_FRAME_BUFFER_VALID,64,32,0);}
size_t retro_serialize_size(void){return 0;} bool retro_serialize(void*d,size_t s){return false;} bool retro_unserialize(const void*d,size_t s){return false;}
bool retro_load_game(const struct retro_game_info*g){ memset(&hw,0,sizeof hw); hw.context_type=RETRO_HW_CONTEXT_OPENGL_CORE;
 if(env(RETRO_ENVIRONMENT_SET_HW_RENDER,&hw)) return false; /* doit refuser le GL de bureau */
 hw.context_type=RETRO_HW_CONTEXT_OPENGLES3; hw.depth=true; hw.stencil=true; hw.bottom_left_origin=true;
 hw.context_reset=reset; hw.context_destroy=destroy; return env(RETRO_ENVIRONMENT_SET_HW_RENDER,&hw);}
void retro_unload_game(void){} void *retro_get_memory_data(unsigned i){return 0;} size_t retro_get_memory_size(unsigned i){return 0;}
