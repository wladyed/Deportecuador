import {Component,signal,HostListener} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {MediaLibrary,MediaAsset} from './media';
interface ContentDocument {key:string;locale:string;label:string;route:string;state:string;visible:boolean;version:number;workingRevision:number;publishedRevision:number|null;updatedAt:string}
interface ContentDetail {document:ContentDocument;fields:Record<string,string>;body:Record<string,string>;revisions:{revision:number;author:string|null;createdAt:string}[];events:{action:string;revision:number;author:string|null;createdAt:string}[]}
@Component({selector:'editorial-workspace',standalone:true,imports:[FormsModule,DatePipe,MediaLibrary],templateUrl:'./editorial.html'})
export class EditorialWorkspace {
  documents=signal<ContentDocument[]>([]);detail=signal<ContentDetail|null>(null);busy=signal(false);error=signal('');notice=signal('');preview=signal<Record<string,string>|null>(null);
  locale='es';body:Record<string,string>={};baseline='';page=0;
  picker=signal<''|'image'|'document'>('');
  selectMedia(asset:MediaAsset){const image=this.picker()==='image';this.body[image?'mediaImage':'mediaDocument']=asset.id;this.body[image?'mediaAlt':'mediaLabel']=asset.title;this.picker.set('');}
  readonly stateNames:Record<string,string>={PUBLISHED:'Publicado',DRAFT:'Borrador',ARCHIVED:'Archivado'};
  readonly actionNames:Record<string,string>={PUBLISH:'Publicación',UNPUBLISH:'Retiro de publicación',ARCHIVE:'Archivo',SHOW:'Visible',HIDE:'Oculto',DRAFT_SAVED:'Borrador guardado'};
  constructor(){this.reload();}
  fields(){return Object.entries(this.detail()?.fields||{});}
  dirty(){return !!this.detail()&&JSON.stringify(this.body)!==this.baseline;}
  @HostListener('window:beforeunload',['$event']) beforeUnload(event:BeforeUnloadEvent){if(this.dirty()){event.preventDefault();event.returnValue='';}}
  limit(key:string){return key==='metaTitle'||key==='name'?180:key==='metaDescription'?400:4000;}
  private async api<T>(path:string,method='GET',body?:unknown):Promise<T>{
    const headers:Record<string,string>={};
    if(method!=='GET'){
      const token=await fetch('/api/admin/auth/csrf',{credentials:'same-origin',cache:'no-store'});
      if(!token.ok)throw new Error('La sesión venció. Vuelve a iniciar sesión.');
      const csrf=await token.json();headers[csrf.headerName]=csrf.token;headers['Content-Type']='application/json';
    }
    const response=await fetch('/api/admin/content'+path,{method,headers,credentials:'same-origin',cache:'no-store',body:body===undefined?undefined:JSON.stringify(body)});
    if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.message||(response.status===401?'La sesión venció. Vuelve a iniciar sesión.':'No se pudo completar la operación.'));}
    return response.status===204?undefined as T:await response.json();
  }
  private async run(action:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');this.notice.set('');try{await action();}catch(error){this.error.set(error instanceof Error?error.message:'Error de conexión.');}finally{this.busy.set(false);}}
  reload(){void this.run(async()=>{this.documents.set(await this.api<ContentDocument[]>('?locale='+this.locale));this.detail.set(null);this.preview.set(null);});}
  private path(){const doc=this.detail()!.document;return '/'+doc.locale+'/'+doc.key;}
  private async load(path:string){const value=await this.api<ContentDetail>(path+'?page='+this.page);this.detail.set(value);this.body={...value.body};this.baseline=JSON.stringify(this.body);this.preview.set(null);this.picker.set('');}
  open(doc:ContentDocument){void this.run(async()=>{this.page=0;await this.load('/'+doc.locale+'/'+doc.key);});}
  discard(){void this.run(()=>this.load(this.path()));}
  save(){void this.run(async()=>{const path=this.path();await this.api(path,'PUT',{version:this.detail()!.document.version,body:this.body});await this.load(path);this.documents.set(await this.api('?locale='+this.locale));this.notice.set('Borrador guardado. La versión pública no cambia hasta que publiques.');});}
  action(action:string,revision?:number){void this.run(async()=>{const path=this.path();await this.api(path+'/actions','POST',{version:this.detail()!.document.version,action,revision});await this.load(path);this.documents.set(await this.api('?locale='+this.locale));this.notice.set(action==='RESTORE'?'Versión recuperada como nuevo borrador. Revisa y publica cuando corresponda.':'Cambio aplicado a este idioma.');});}
  viewRevision(revision:number){void this.run(async()=>{this.preview.set(await this.api(this.path()+'/revisions/'+revision));});}
  history(page:number){void this.run(async()=>{this.page=page;await this.load(this.path());});}
}
