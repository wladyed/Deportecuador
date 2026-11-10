import {Component,Input,Output,EventEmitter,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
export interface MediaAsset {id:string;filename:string;title:string;contentType:string;size:number;sha256:string;width:number|null;height:number|null;archived:boolean;version:number;createdAt:string}
@Component({selector:'media-library',standalone:true,imports:[FormsModule],templateUrl:'./media.html',styleUrl:'./media.css'})
export class MediaLibrary {
 @Input() picker:''|'image'|'document'='';@Output() selected=new EventEmitter<MediaAsset>();
 items=signal<MediaAsset[]>([]);total=signal(0);busy=signal(false);error=signal('');notice=signal('');q='';archived=false;page=0;title='';file:File|null=null;
 constructor(){this.reload();}
 image(asset:MediaAsset){return asset.contentType.startsWith('image/');}
 accepts(asset:MediaAsset){return this.picker==='image'?this.image(asset):!this.image(asset);}
 url(id:string){return '/api/admin/media/'+id+'/file';}
 choose(event:Event){const input=event.target as HTMLInputElement;this.file=input.files?.[0]||null;if(this.file&&!this.title)this.title=this.file.name.replace(/\.[^.]+$/,'');}
 private async api(path='',method='GET',body?:unknown){const headers:Record<string,string>={};if(method!=='GET'){const r=await fetch('/api/admin/auth/csrf',{cache:'no-store'});if(!r.ok)throw Error('La sesión venció.');const c=await r.json();headers[c.headerName]=c.token;if(!(body instanceof FormData))headers['Content-Type']='application/json';}const r=await fetch('/api/admin/media'+path,{method,headers,cache:'no-store',body:body===undefined?undefined:body instanceof FormData?body:JSON.stringify(body)});if(!r.ok)throw Error(r.status===413?'El archivo supera el máximo de 10 MiB.':r.status===409?'El archivo cambió. Actualiza la biblioteca.':r.status===400?'Revisa título y archivo: JPG/PNG legibles o PDF, máximo 10 MiB; imagen hasta 8000 píxeles por lado y 20 megapíxeles.':'No se pudo completar. Comprueba la conexión y los permisos.');return r.status===204?null:await r.json();}
 private async run(action:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');this.notice.set('');try{await action();}catch(e){this.error.set(e instanceof Error?e.message:'Error de conexión.');}finally{this.busy.set(false);}}
 private async load(){const data=await this.api('?page='+this.page+'&archived='+this.archived+'&q='+encodeURIComponent(this.q));this.items.set(data.items);this.total.set(data.total);}
 reload(page=0){this.page=page;void this.run(()=>this.load());}
 upload(input:HTMLInputElement){void this.run(async()=>{if(!this.file||this.file.size>10*1024*1024)throw Error('Selecciona un archivo de hasta 10 MiB.');const data=new FormData();data.append('file',this.file);data.append('title',this.title);await this.api('','POST',data);this.file=null;this.title='';input.value='';this.q='';this.archived=false;this.page=0;await this.load();this.notice.set('Archivo guardado. Será público cuando publiques una página que lo utilice.');});}
 save(asset:MediaAsset,archived=asset.archived){void this.run(async()=>{await this.api('/'+asset.id,'PATCH',{version:asset.version,title:asset.title,archived});await this.load();this.notice.set(archived?'Archivado en la biblioteca. Las páginas y versiones que lo utilizan conservan acceso.':'Cambios guardados.');});}
}
