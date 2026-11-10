import {Component,Input,signal,OnChanges,SimpleChanges,afterNextRender} from '@angular/core';
import {FormsModule} from '@angular/forms';
interface Slot {id:string;start:string;end:string}
@Component({selector:'public-assistant',standalone:true,imports:[FormsModule],templateUrl:'./assistant.html',styleUrl:'./assistant.css'})
export class PublicAssistant implements OnChanges {
 @Input() lang=0;
 @Input() topics:{id:string;kind:string;name:string}[]=[];
 open=signal(false);booking=signal(false);busy=signal(false);error=signal('');whatsapp=signal('');calendarReady=signal(false);slots=signal<Slot[]>([]);messages=signal<{who:string;text:string}[]>([]);receipt=signal('');
 question='';slotId='';interest='';key='';contact={organization:'',country:'',name:'',email:'',phone:'',consent:false,website:''};
 readonly labels=[
 ['Chat y citas','Asistente automático','Cerrar','Consulta sobre productos o servicios. No compartas datos sensibles.','Escribe tu consulta','Enviar','Reservar demostración o reunión','Horarios disponibles','No hay horarios publicados. Escríbenos a info@deportecuador.com.ec.','Selecciona','Tema','Organización','País','Nombre','Correo electrónico','Teléfono (opcional)','Acepto el tratamiento de mis datos para gestionar esta cita.','Política de privacidad','Confirmar reserva','Reserva confirmada. Referencia: ','No se pudo completar. Revisa la conexión e inténtalo de nuevo.','Ese horario ya no está disponible. Actualiza la agenda.','Volver al chat','Hora de Quito (America/Guayaquil)','WhatsApp','Procesando…'],
 ['Chat and appointments','Automated assistant','Close','Ask about products or services. Do not share sensitive information.','Your question','Send','Book a demo or meeting','Available appointments','No times are published. Email info@deportecuador.com.ec.','Select','Topic','Organization','Country','Name','Email','Phone (optional)','I agree to the processing of my data to manage this appointment.','Privacy policy','Confirm booking','Booking confirmed. Reference: ','Unable to complete. Check your connection and try again.','That time is no longer available. Refresh the calendar.','Back to chat','Quito time (America/Guayaquil)','WhatsApp','Processing…'],
 ['Chat e reuniões','Assistente automático','Fechar','Pergunte sobre produtos ou serviços. Não compartilhe dados sensíveis.','Sua pergunta','Enviar','Reservar demonstração ou reunião','Horários disponíveis','Não há horários publicados. Escreva para info@deportecuador.com.ec.','Selecione','Tema','Organização','País','Nome','E-mail','Telefone (opcional)','Aceito o tratamento dos meus dados para gerenciar esta reunião.','Política de privacidade','Confirmar reserva','Reserva confirmada. Referência: ','Não foi possível concluir. Verifique a conexão e tente novamente.','Esse horário não está mais disponível. Atualize a agenda.','Voltar ao chat','Hora de Quito (America/Guayaquil)','WhatsApp','Processando…']
 ];
 t(i:number){return this.labels[this.lang][i];} get locale(){return ['es','en','pt'][this.lang];}
 constructor(){afterNextRender(()=>{void this.configuration();});}
 async configuration(){try{const res=await fetch('/api/v1/assistant/config',{cache:'no-store'});if(res.ok){const c=await res.json();this.whatsapp.set(c.whatsapp);this.calendarReady.set(c.calendarReady);}}catch{/* No contact is fabricated when configuration fails. */}}
 ngOnChanges(changes:SimpleChanges){if(changes['lang']){this.messages.set([]);this.error.set('');}}
 async toggle(){this.open.update(v=>!v);if(this.open())await this.configuration();}
 pendingCalendar(){return ['Las reservas estarán disponibles cuando se conecte Google Calendar. Puedes consultar nuestros productos o escribir a info@deportecuador.com.ec.','Bookings will be available once Google Calendar is connected. Ask about our products or email info@deportecuador.com.ec.','As reservas estarão disponíveis quando o Google Calendar estiver conectado. Consulte nossos produtos ou escreva para info@deportecuador.com.ec.'][this.lang];}
 when(slot:Slot){return new Intl.DateTimeFormat(this.locale,{timeZone:'America/Guayaquil',dateStyle:'medium',timeStyle:'short'}).format(new Date(slot.start))+' – '+new Intl.DateTimeFormat(this.locale,{timeZone:'America/Guayaquil',timeStyle:'short'}).format(new Date(slot.end));}
 async ask(topic?:string){
  const message=(topic||this.question).trim();if(!message||this.busy())return;this.busy.set(true);this.error.set('');this.question='';
  this.messages.update(m=>[...m.slice(-18),{who:'you',text:message}]);
  try{const res=await fetch('/api/v1/assistant/message',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message,locale:this.locale})});if(!res.ok)throw Error();const data=await res.json();this.messages.update(m=>[...m,{who:'bot',text:data.matches.length?data.matches.map((x:{name:string;description:string})=>x.name+'\n'+x.description).join('\n\n'):data.message}]);}catch{this.error.set(this.t(20));}finally{this.busy.set(false);}
 }
 async agenda(){if(this.busy())return;this.busy.set(true);this.error.set('');this.booking.set(true);try{const res=await fetch('/api/v1/assistant/slots',{cache:'no-store'});if(!res.ok)throw Error();this.slots.set(await res.json());if(!this.slots().some(s=>s.id===this.slotId)){this.slotId='';this.key='';}}catch{this.error.set(this.t(20));}finally{this.busy.set(false);}}
 async reserve(){
  if(this.busy()||!this.slotId||!this.interest||!this.contact.consent)return;
  const topic=this.topics.find(t=>t.id===this.interest);if(!topic)return;this.busy.set(true);this.error.set('');this.key||=crypto.randomUUID();
  try{const res=await fetch('/api/v1/assistant/bookings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':this.key},body:JSON.stringify({slotId:this.slotId,contact:{...this.contact,type:topic.kind==='product'?'DEMO':'ADVICE',description:'Appointment / Cita / Reunião',interests:[this.interest],locale:this.locale,policyVersion:'draft-2026-10-19'}})});if(!res.ok){if(res.status===409){this.error.set(this.t(21));return;}throw Error();}const data=await res.json();if(data.status!=='BOOKED')throw Error();const slot=this.slots().find(s=>s.id===this.slotId)!;this.receipt.set(this.t(19)+data.id+' · '+this.when(slot));this.contact={organization:'',country:'',name:'',email:'',phone:'',consent:false,website:''};this.key='';this.slotId='';}catch{this.error.set(this.t(20));}finally{this.busy.set(false);}
 }
}
