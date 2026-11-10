import {AccountSecurity} from './account-security';
import {Component, signal, ViewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {EditorialWorkspace} from './editorial';
import {MediaLibrary} from './media';
import {AppointmentWorkspace} from './appointments';
import {AssistantSettingsWorkspace} from './assistant-settings';

type Role = 'ADMIN'|'EDITOR'|'COMMERCIAL'|'VIEWER';
interface Identity {id:string; email:string; name:string; role:Role}
interface User extends Identity {enabled:boolean; enrolled:boolean}
interface RequestItem {id:string; type:string; organization:string; country:string; name:string; email:string; phone:string; description:string; interests:string[]; locale:string; status:string; createdAt:string; assignedTo:string|null; assignedName:string|null}
interface RequestPage {items:RequestItem[]; total:number; page:number; size:number}
interface Summary {type:string; status:string; count:number}
interface Audit {id:string; actorId:string|null; action:string; targetId:string|null; createdAt:string}
interface CrmDetail {workflow:{status:string;version:number;nextContactAt:string|null;closeReason:string|null;closedAt:string|null};activities:{id:string;actor:string;kind:string;body:string;createdAt:string}[]}
interface CrmNotice {id:string;requestId:string;kind:string;createdAt:string;readAt:string|null}
class ApiError extends Error {constructor(public status:number,message:string){super(message);}}

@Component({selector:'admin-root', standalone:true, imports:[AccountSecurity,FormsModule,DatePipe,MediaLibrary,EditorialWorkspace,AppointmentWorkspace,AssistantSettingsWorkspace], templateUrl:'./app.html'})
export class AdminApp {
  @ViewChild(EditorialWorkspace) private editorial?:EditorialWorkspace;
  user=signal<Identity|null>(null); busy=signal(false); loading=signal(true); error=signal(''); notice=signal('');
  screen=signal<'login'|'enroll'|'mfa'|'forgot'|'reset'>('login'); setupRequired=signal(false); enrollmentSecret=signal('');
  pane=signal<'requests'|'users'|'reports'|'audit'|'editor'|'appointments'|'assistantSettings'|'media'|'security'>('requests');
  rows=signal<RequestPage>({items:[],total:0,page:0,size:20}); selected=signal<RequestItem|null>(null);
  users=signal<User[]>([]); reports=signal<Summary[]>([]); audit=signal<Audit[]>([]);
  invitation=signal(''); invitationExpiry=signal('');
  crm=signal<CrmDetail|null>(null); notifications=signal<CrmNotice[]>([]);
  statusFilter=''; dueOnly=false; workflowStatus='NEW'; nextContact=''; closeReason=''; activityKind='NOTE'; activityBody=''; activityPage=0;
  readonly statusNames:Record<string,string>={NEW:'Nueva',CONTACTED:'Contactada',PROPOSAL:'Propuesta',WON:'Ganada',LOST:'Perdida'};
  readonly activityNames:Record<string,string>={NOTE:'Nota',CALL:'Llamada',MEETING:'Reunión',EMAIL:'Correo',WORKFLOW:'Seguimiento',ASSIGNMENT:'Asignación'};
  readonly states=Object.keys(this.statusNames);
  readonly roleNames:Record<Role,string>={ADMIN:'Administrador',EDITOR:'Editor',COMMERCIAL:'Comercial',VIEWER:'Consulta'};
  readonly roles:Role[]=['ADMIN','EDITOR','COMMERCIAL','VIEWER'];
  googleEnabled=signal(false);resetToken='';confirmPassword='';
  email=''; password=''; name=''; activationToken=''; code=''; type=''; assignedTo='';
  newUser={email:'',name:'',role:'COMMERCIAL' as Role};
  private csrf:{headerName:string;token:string}|null=null;

  constructor(){const params=new URLSearchParams(location.hash.slice(1));this.resetToken=params.get('reset')||'';if(this.resetToken){this.screen.set('reset');history.replaceState(null,'','/admin/');}if(new URLSearchParams(location.search).has('authError'))this.error.set('No se pudo vincular o acceder con Google. Comprueba el dominio y la cuenta vinculada.');void this.initialize();}
  private async api<T>(path:string, method='GET', data?:unknown):Promise<T> {
    const headers:Record<string,string>={};
    if(method!=='GET') {
      if(!this.csrf) this.csrf=await this.api('/auth/csrf');
      headers[this.csrf!.headerName]=this.csrf!.token; headers['Content-Type']='application/json';
    }
    const response=await fetch('/api/admin'+path,{method,headers,credentials:'same-origin',cache:'no-store',body:data===undefined?undefined:JSON.stringify(data)});
    if(!response.ok) {
      const body=await response.json().catch(()=>({}));
      throw new ApiError(response.status,body.message || (response.status===401?'La sesión venció o las credenciales no son válidas.':response.status===403?'No tienes permiso o la sesión de seguridad venció. Recarga la página.':response.status===429?'Demasiados intentos. Espera un minuto antes de continuar.':'No se pudo completar la operación.'));
    }
    return response.status===204?undefined as T:await response.json() as T;
  }
  private async initialize(){
    try {
      const status=await this.api<{setupRequired:boolean;mfaRequired:boolean;googleEnabled:boolean}>('/auth/status');this.setupRequired.set(status.setupRequired);this.googleEnabled.set(status.googleEnabled);if(status.mfaRequired&&!this.resetToken)this.screen.set('mfa');
      let identity:Identity|null=null;
      try {identity=await this.api<Identity>('/auth/me');}catch(e){if(!(e instanceof ApiError)||e.status!==401)throw e;}
      if(identity&&!this.resetToken){this.user.set(identity);await this.workspace();}
    }catch(e){this.error.set(e instanceof Error?e.message:'No se pudo conectar al servidor.');}
    finally{this.loading.set(false);}
  }
  async run(action:()=>Promise<void>){
    if(this.busy())return;this.busy.set(true);this.error.set('');this.notice.set('');
    try{await action();}catch(e){
      if(e instanceof ApiError && e.status===401 && this.user())this.clearSession();
      this.error.set(e instanceof Error?e.message:'No se pudo completar la operación.');
    }finally{this.busy.set(false);}
  }
  login(){void this.run(async()=>{
    await this.api('/auth/login','POST',{email:this.email,password:this.password});this.password='';this.code='';this.screen.set('mfa');
  });}
  forgot(){void this.run(async()=>{const result=await this.api<{message:string}>('/auth/password/request','POST',{email:this.email});this.notice.set(result.message);});}
  resetPassword(){void this.run(async()=>{if(this.password!==this.confirmPassword)throw new Error('Las contraseñas no coinciden.');await this.api('/auth/password/reset','POST',{token:this.resetToken,password:this.password});this.resetToken='';this.confirmPassword='';this.csrf=null;this.backToLogin();this.notice.set('Contraseña actualizada. Inicia sesión y confirma tu segundo factor.');});}
  enroll(){void this.run(async()=>{
    const result=await this.api<{secret:string;account:string}>('/auth/enroll','POST',{token:this.activationToken,email:this.email,name:this.name,password:this.password,bootstrap:this.setupRequired()});
    this.password='';this.activationToken='';this.email=result.account;this.enrollmentSecret.set(result.secret);this.code='';this.screen.set('mfa');
  });}
  verify(){void this.run(async()=>{
    const identity=await this.api<Identity>('/auth/mfa','POST',{code:this.code});this.code='';this.csrf=null;this.enrollmentSecret.set('');this.setupRequired.set(false);this.user.set(identity);await this.workspace();
  });}
  backToLogin(){this.screen.set('login');this.enrollmentSecret.set('');this.password='';this.code='';this.activationToken='';this.error.set('');}
  logout(){void this.run(async()=>{await this.api('/auth/logout','POST',{});this.clearSession();});}
  private clearSession(){this.user.set(null);this.csrf=null;this.rows.set({items:[],total:0,page:0,size:20});this.selected.set(null);this.crm.set(null);this.notifications.set([]);this.activityBody='';this.closeReason='';this.nextContact='';this.users.set([]);this.reports.set([]);this.audit.set([]);this.invitation.set('');this.backToLogin();}
  private async workspace(){
    const role=this.user()!.role;
    this.pane.set(role==='EDITOR'?'editor':role==='VIEWER'?'reports':'requests');
    if(role==='ADMIN')this.users.set(await this.api<User[]>('/users'));
    if(role==='ADMIN'||role==='COMMERCIAL')await this.loadRequests(0);
    if(role==='VIEWER')this.reports.set(await this.api<Summary[]>('/reports'));
  }
  navigate(pane:'requests'|'users'|'reports'|'audit'|'editor'|'appointments'|'assistantSettings'|'media'|'security'){
    if(this.editorial?.dirty()||this.editorial?.busy()){this.error.set('Guarda o descarta los cambios editoriales y espera a que termine la operación antes de cambiar de sección.');return;}
    void this.run(async()=>{
    this.pane.set(pane);this.invitation.set('');
    if(pane==='requests')await this.loadRequests(0);
    if(pane==='users')this.users.set(await this.api<User[]>('/users'));
    if(pane==='reports')this.reports.set(await this.api<Summary[]>('/reports'));
    if(pane==='audit')this.audit.set(await this.api<Audit[]>('/audit'));
  });}
  private async loadRequests(page:number){this.rows.set(await this.api<RequestPage>('/requests?page='+page+'&type='+encodeURIComponent(this.type)+'&status='+encodeURIComponent(this.statusFilter)+'&due='+this.dueOnly));this.selected.set(null);this.crm.set(null);this.notifications.set(await this.api<CrmNotice[]>('/notifications'));}
  reload(page=0){void this.run(()=>this.loadRequests(page));}
  select(row:RequestItem){void this.run(async()=>{this.selected.set(null);this.crm.set(null);this.activityPage=0;this.activityBody='';await this.loadCrm(row.id);this.selected.set(row);this.assignedTo=row.assignedTo||'';});}
  private async loadCrm(id:string){
    const detail=await this.api<CrmDetail>('/requests/'+id+'/crm?page='+this.activityPage);this.crm.set(detail);
    this.workflowStatus=detail.workflow.status;this.closeReason=detail.workflow.closeReason||'';
    const date=detail.workflow.nextContactAt?new Date(detail.workflow.nextContactAt):null;
    this.nextContact=date?new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16):'';
  }
  workflowOptions(){const status=this.crm()?.workflow.status||'NEW';return status==='NEW'?['NEW','CONTACTED','LOST']:status==='CONTACTED'?['CONTACTED','PROPOSAL','WON','LOST']:status==='PROPOSAL'?['PROPOSAL','CONTACTED','WON','LOST']:this.user()?.role==='ADMIN'?[status,'CONTACTED']:[status];}
  isClosed(){return ['WON','LOST'].includes(this.crm()?.workflow.status||'');}
  saveWorkflow(){void this.run(async()=>{
    const id=this.selected()!.id;
    await this.api('/requests/'+id+'/workflow','PATCH',{version:this.crm()!.workflow.version,status:this.workflowStatus,nextContactAt:['WON','LOST'].includes(this.workflowStatus)?null:this.nextContact?new Date(this.nextContact).toISOString():null,closeReason:this.closeReason});
    await this.loadCrm(id);this.rows.update(page=>({...page,items:page.items.map(row=>row.id===id?{...row,status:this.workflowStatus}:row)}));this.notifications.set(await this.api<CrmNotice[]>('/notifications'));this.notice.set('Seguimiento guardado.');
  });}
  addActivity(){void this.run(async()=>{const id=this.selected()!.id;await this.api('/requests/'+id+'/activities','POST',{kind:this.activityKind,body:this.activityBody});this.activityBody='';this.activityPage=0;await this.loadCrm(id);this.notice.set('Actividad registrada.');});}
  historyPage(page:number){void this.run(async()=>{this.activityPage=page;await this.loadCrm(this.selected()!.id);});}
  markRead(item:CrmNotice){void this.run(async()=>{await this.api('/notifications/'+item.id+'/read','PATCH',{});this.notifications.set(await this.api<CrmNotice[]>('/notifications'));});}
  assignees(){return this.users().filter(u=>u.enabled&&u.enrolled&&(u.role==='ADMIN'||u.role==='COMMERCIAL'));}
  assign(){void this.run(async()=>{
    await this.api('/requests/'+this.selected()!.id+'/assignment','PATCH',{userId:this.assignedTo||null});await this.loadRequests(this.rows().page);this.notice.set('Responsable actualizado.');
  });}
  invite(){void this.run(async()=>{
    const result=await this.api<{token:string;expiresAt:string}>('/users','POST',this.newUser);this.invitation.set(result.token);this.invitationExpiry.set(result.expiresAt);this.newUser={email:'',name:'',role:'COMMERCIAL'};this.users.set(await this.api<User[]>('/users'));this.notice.set('Invitación creada. Entrega el código a su destinatario por un canal privado.');
  });}
  saveUser(row:User){void this.run(async()=>{
    await this.api('/users/'+row.id,'PATCH',{role:row.role,enabled:row.enabled});this.users.set(await this.api<User[]>('/users'));
    this.user.set(await this.api<Identity>('/auth/me'));if(this.user()!.role!=='ADMIN')await this.workspace();this.notice.set('Permisos guardados.');
  });}
  renewInvitation(row:User){void this.run(async()=>{
    const result=await this.api<{token:string;expiresAt:string}>('/users/'+row.id+'/invitation','POST',{});
    this.invitation.set(result.token);this.invitationExpiry.set(result.expiresAt);this.notice.set('Nuevo código para '+row.email+'. El código anterior ya no es válido.');
  });}
  total(){return this.reports().reduce((sum,item)=>sum+item.count,0);}
}
