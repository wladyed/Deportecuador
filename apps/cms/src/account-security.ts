import {Component,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';

@Component({selector:'account-security',standalone:true,imports:[FormsModule],template:`
<div class="heading"><div><p class="eyebrow">MI CUENTA</p><h1>Seguridad de acceso</h1><p>Protege tu cuenta y prepara una alternativa si pierdes el autenticador.</p></div></div>
@if(error()){<p class="error" role="alert">{{error()}}</p>}
@if(notice()){<p class="notice" role="status">{{notice()}}</p>}
<section class="panel"><h2>Códigos de recuperación</h2><p>Disponibles: {{remaining()}}. Cada código permite un único acceso junto con tu contraseña. Generar nuevos invalida los anteriores.</p>
@if(codes().length){<p><strong>Guárdalos ahora en un lugar privado. No volverán a mostrarse.</strong></p><pre style="white-space:pre-wrap;overflow-wrap:anywhere">{{codes().join('\n')}}</pre><button class="secondary" (click)="codes.set([])">Ya los guardé; ocultar</button>}
<h2>Confirma tu identidad</h2><p>Para cada cambio, escribe tu contraseña y un código nuevo del autenticador o un código de recuperación. Si acabas de entrar con recuperación, puedes reemplazar el autenticador durante cinco minutos usando tu contraseña.</p>
<label>Contraseña actual<input type="password" [(ngModel)]="password" autocomplete="current-password" maxlength="72"></label>
<label>Código actual o de recuperación<input [(ngModel)]="code" autocomplete="one-time-code" maxlength="32"></label>
<div class="actions"><button class="primary" (click)="generate()" [disabled]="busy()||!password||!code">Generar diez códigos</button><button class="secondary" (click)="replace()" [disabled]="busy()||!password">Reemplazar autenticador</button></div>
@if(secret()){<h3>Configura el nuevo autenticador</h3><p>Añade una clave basada en tiempo: 6 dígitos y 30 segundos. La anterior sigue activa hasta confirmar.</p><code class="secret">{{secret()}}</code><label>Código del nuevo autenticador<input [(ngModel)]="newCode" autocomplete="one-time-code" maxlength="6"></label><button class="primary" (click)="finish()" [disabled]="busy()||newCode.length!==6">Confirmar y cerrar sesiones</button><p>Al confirmar, se invalidan los códigos de recuperación anteriores. Genera otros después de volver a entrar.</p>}
</section>
<section class="panel"><h2>Google Workspace</h2><p>{{googleEnabled()?'Vincula tu cuenta empresarial. Google sustituirá la contraseña al iniciar sesión; se mantiene el segundo factor del panel.':'El acceso con Google está pendiente de configuración del servidor.'}}</p><p>{{linked()?'Cuenta de Google vinculada.':'Sin cuenta vinculada.'}}</p>
@if(googleEnabled()&&!linked()){<button class="primary" (click)="link()" [disabled]="busy()||!password||!code">Confirmar identidad y vincular Google</button>}
@if(linked()){<button class="secondary" (click)="unlink()" [disabled]="busy()||!password||!code">Desvincular Google y cerrar sesiones</button>}
</section>`})
export class AccountSecurity {
 busy=signal(false);error=signal('');notice=signal('');remaining=signal(0);googleEnabled=signal(false);linked=signal(false);codes=signal<string[]>([]);secret=signal('');password='';code='';newCode='';
 constructor(){void this.run(async()=>{const status=await this.api('/security');this.remaining.set(status.recoveryCodes);this.googleEnabled.set(status.googleEnabled);this.linked.set(status.googleLinked);});}
 private async api(path:string,data?:unknown):Promise<any>{const headers:Record<string,string>={};if(data!==undefined){const csrf=await fetch('/api/admin/auth/csrf',{cache:'no-store'}).then(r=>r.json());headers[csrf.headerName]=csrf.token;headers['Content-Type']='application/json';}const response=await fetch('/api/admin/auth'+path,{method:data===undefined?'GET':'POST',headers,cache:'no-store',body:data===undefined?undefined:JSON.stringify(data)});const body=await response.json().catch(()=>({}));if(!response.ok)throw new Error(body.message||'No se pudo completar. Revisa las credenciales o vuelve a iniciar sesión.');return body;}
 private async run(action:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');try{await action();}catch(e){this.error.set(e instanceof Error?e.message:'No se pudo completar.');}finally{this.password='';this.code='';this.busy.set(false);}}
 private proof(){return {password:this.password,code:this.code};}
 generate(){void this.run(async()=>{const result=await this.api('/security/codes',this.proof());this.codes.set(result.codes);this.remaining.set(result.codes.length);});}
 replace(){void this.run(async()=>{const result=await this.api('/security/mfa/start',this.proof());this.secret.set(result.secret);this.newCode='';});}
 finish(){void this.run(async()=>{await this.api('/security/mfa/finish',{code:this.newCode});location.assign('/admin/');});}
 link(){void this.run(async()=>{const result=await this.api('/security/google/link',this.proof());location.assign(result.url);});}
 unlink(){void this.run(async()=>{await this.api('/security/google/unlink',this.proof());location.assign('/admin/');});}
}
