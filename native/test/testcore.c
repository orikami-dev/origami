#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdio.h>
#include "libretro.h"
static retro_environment_t env; static retro_video_refresh_t video; static retro_audio_sample_batch_t audio;
static retro_input_state_t input; static retro_input_poll_t poll_cb;
static uint16_t fb[320*240]; static uint8_t sram[8192]; static int frame; static int16_t snd[2*800];
static int loaded_size; static char varval[64];
void retro_set_environment(retro_environment_t e){ env=e;
  static const struct retro_variable vars[]={{"test_speed","Vitesse; lent|rapide"},{NULL,NULL}};
  e(RETRO_ENVIRONMENT_SET_VARIABLES,(void*)vars);}
void retro_set_video_refresh(retro_video_refresh_t v){video=v;}
void retro_set_audio_sample(retro_audio_sample_t a){(void)a;}
void retro_set_audio_sample_batch(retro_audio_sample_batch_t a){audio=a;}
void retro_set_input_poll(retro_input_poll_t p){poll_cb=p;}
void retro_set_input_state(retro_input_state_t i){input=i;}
void retro_set_controller_port_device(unsigned a,unsigned b){(void)a;(void)b;}
unsigned retro_api_version(void){return RETRO_API_VERSION;}
void retro_init(void){ enum retro_pixel_format f=RETRO_PIXEL_FORMAT_RGB565; env(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT,&f);
  struct retro_log_callback l; if(env(RETRO_ENVIRONMENT_GET_LOG_INTERFACE,&l)) l.log(RETRO_LOG_INFO,"init %d\n",42);}
void retro_deinit(void){}
void retro_get_system_info(struct retro_system_info*i){memset(i,0,sizeof *i);i->library_name="testcore";i->library_version="1";i->valid_extensions="tst|bin";i->need_fullpath=false;}
void retro_get_system_av_info(struct retro_system_av_info*i){memset(i,0,sizeof *i);i->geometry.base_width=320;i->geometry.base_height=240;i->geometry.max_width=320;i->geometry.max_height=240;i->geometry.aspect_ratio=4.0f/3;i->timing.fps=60;i->timing.sample_rate=48000;}
void retro_reset(void){frame=0;}
void retro_run(void){ poll_cb();
  int a = input(0,RETRO_DEVICE_JOYPAD,0,RETRO_DEVICE_ID_JOYPAD_A);
  int mask = input(0,RETRO_DEVICE_JOYPAD,0,RETRO_DEVICE_ID_JOYPAD_MASK);
  struct retro_variable v={"test_speed",NULL}; env(RETRO_ENVIRONMENT_GET_VARIABLE,&v); snprintf(varval,sizeof varval,"%s",v.value?v.value:"(null)");
  for(int i=0;i<320*240;i++) fb[i]= a ? 0xF800 : 0x07E0;  /* rouge si A, sinon vert */
  fb[0]=0x001F; /* bleu pur */
  sram[0]=(uint8_t)mask; frame++;
  for(int i=0;i<1600;i++) snd[i]=(int16_t)(i+frame);
  video(fb,320,240,320*2); audio(snd,800);}
size_t retro_serialize_size(void){return 4;}
bool retro_serialize(void*d,size_t n){(void)n;memcpy(d,&frame,4);return true;}
bool retro_unserialize(const void*d,size_t n){(void)n;memcpy(&frame,d,4);return true;}
static int cheats; void retro_cheat_reset(void){cheats=0;} void retro_cheat_set(unsigned a,bool b,const char*c){(void)a;if(b&&c&&c[0])cheats++;}
int test_cheats(void){return cheats;}
bool retro_load_game(const struct retro_game_info*g){ if(!g||!g->data) return false; loaded_size=(int)g->size; return true;}
bool retro_load_game_special(unsigned a,const struct retro_game_info*b,size_t c){(void)a;(void)b;(void)c;return false;}
void retro_unload_game(void){}
unsigned retro_get_region(void){return 0;}
void*retro_get_memory_data(unsigned id){return id==0?sram:NULL;}
size_t retro_get_memory_size(unsigned id){return id==0?sizeof sram:0;}
const char* test_varval(void){return varval;}
int test_loaded(void){return loaded_size;}
int test_frame(void){return frame;}
