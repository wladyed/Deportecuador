import {Component,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
interface Slot {id:string;start:string;end:string;state:string;requestId:string|null}
@Component({selector:'appointment-workspace',standalone:true,imports:[FormsModule],template:`
 <div class="heading"><div><p class="eyebrow">DEMOSTRACIONES Y REUNIONES</p><h1>Agenda de citas</h1><p>Una agenda compartida. Solo los horarios publicados aquí se ofrecen en el chat. Las reservas crean solicitudes sin asignar en el CRM.</p></div></div>
 @if(error()){<p class="error" role="alert">{{error()}}</p>}
 <section class="panel"><h2>Publicar horarios disponibles</h2><p>Aplica los días, duración y horario definidos en WhatsApp y reservas. Anticipación mínima: una hora. No crea eventos en Google hasta que alguien reserve.</p><button class="secondary" (click)="generate()" [disabled]="busy()">Generar próximos 14 días</button><p>También puedes añadir un horario individual. Introduce hora de Quito (America/Guayaquil).</p>
 <form (ngSubmit)="create()" class="invite-grid"><label>Inicio<input type="datetime-local" name="start" [(ngModel)]="start" required></label><label>Fin<input type="datetime-local" name="end" [(ngModel)]="end" required></label><button class="primary" [disabled]="busy()">Publicar horario</button></form></section>
 <section class="panel"><h2>Horarios y reservas</h2><p>Últimos 200 horarios. Los datos de contacto se consultan desde la solicitud vinculada en la bandeja.</p><button class="secondary" (click)="reload()" [disabled]="busy()">Actualizar</button><div class="table-scroll"><table><thead><tr><th>Hora de Quito</th><th>Estado</th><th>Referencia CRM</th><th>Acción</th></tr></thead><tbody>@for(slot of slots();track slot.id){<tr><td>{{when(slot.start)}}<br>{{when(slot.end)}}</td><td>{{names[slot.state]}}</td><td class="reference">{{slot.requestId||'—'}}</td><td>@if(slot.state!=='CANCELLED'){<button class="secondary" (click)="cancel(slot)" [disabled]="busy()">Cancelar</button>}</td></tr>}@empty{<tr><td colspan="4">Todavía no hay horarios. No se publican disponibilidades ficticias.</td></tr>}</tbody></table></div></section>
 `})
export class AppointmentWorkspace {
 slots=signal<Slot[]>([]);busy=signal(false);error=signal('');start='';end='';names:Record<string,string>={OPEN:'Disponible',BOOKED:'Reservada',CANCELLED:'Cancelada'};
 constructor(){this.reload();}
 when(value:string){return new Intl.DateTimeFormat('es',{timeZone:'America/Guayaquil',dateStyle:'medium',timeStyle:'short'}).format(new Date(value));}
 async api(path='',method='GET',body?:unknown){const headers:Record<string,string>={};if(method!=='GET'){const r=await fetch('/api/admin/auth/csrf',{cache:'no-store'});if(!r.ok)throw Error('La sesión venció.');const c=await r.json();headers[c.headerName]=c.token;headers['Content-Type']='application/json';}const r=await fetch('/api/admin/appointments'+path,{method,headers,cache:'no-store',body:body===undefined?undefined:JSON.stringify(body)});if(!r.ok)throw Error(r.status===409?'El horario se solapa con otro.':r.status===400?'Revisa las fechas y la duración.':'No se pudo completar. Comprueba tu sesión y conexión.');return r.status===204?null:await r.json();}
 async run(action:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');try{await action();}catch(e){this.error.set(e instanceof Error?e.message:'Error de conexión.');}finally{this.busy.set(false);}}
 reload(){void this.run(async()=>{this.slots.set(await this.api());});}
 generate(){void this.run(async()=>{await this.api('/generate','POST',{});this.slots.set(await this.api());});}
 create(){void this.run(async()=>{await this.api('','POST',{start:new Date(this.start+':00-05:00').toISOString(),end:new Date(this.end+':00-05:00').toISOString()});this.start='';this.end='';this.slots.set(await this.api());});}
 cancel(slot:Slot){if(!confirm(slot.state==='BOOKED'?'¿Cancelar esta reserva y notificar al solicitante por correo?':'¿Retirar este horario?'))return;void this.run(async()=>{await this.api('/'+slot.id+'/cancel','POST',{});this.slots.set(await this.api());});}
}
