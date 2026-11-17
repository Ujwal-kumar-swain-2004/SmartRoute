import {useEffect} from 'react';
import {MapContainer,Polyline,CircleMarker,Tooltip,useMap} from 'react-leaflet';
import {LocateFixed,Plus,Minus} from 'lucide-react';
import type {Network,Route,Request} from './types';
import OpenStreetMapTiles from './OpenStreetMapTiles';
function MapControls({points}:{points:[number,number][]}){const map=useMap();useEffect(()=>{if(points.length)map.fitBounds(points,{padding:[65,65],maxZoom:14});},[map,JSON.stringify(points)]);return <div className="map-controls"><button title="Zoom in" onClick={()=>map.zoomIn()}><Plus size={18}/></button><button title="Zoom out" onClick={()=>map.zoomOut()}><Minus size={18}/></button><button title="Fit journey" onClick={()=>map.fitBounds(points,{padding:[65,65],maxZoom:14})}><LocateFixed size={18}/></button></div>}
export default function RouteMap({network,routes,selected,request,onSelect}:{network:Network;routes:Route[];selected:number;request:Request;onSelect:(i:number)=>void}){
 const byId=Object.fromEntries(network.places.map(p=>[p.id,p]));const route=routes[selected];
 const coord=(id:string):[number,number]=>[byId[id].lat,byId[id].lng];const points=(route?.nodes??[request.source,request.destination]).map(coord);
 return <div className="map-wrap"><MapContainer center={[20.296,85.824]} zoom={13} zoomControl={false} className="map" attributionControl={true}>
 <OpenStreetMapTiles/>
 {network.roads.map(e=><Polyline key={e.id} positions={[coord(e.from),coord(e.to)]} pathOptions={{color:'#b9c9c4',weight:2,opacity:.65}}><Tooltip>Synthetic road · {e.km.toFixed(1)} km</Tooltip></Polyline>)}
 {routes.map((r,i)=>i!==selected&&<Polyline key={r.id} positions={r.nodes.map(coord)} pathOptions={{color:'#8b95aa',weight:6,opacity:.65,dashArray:'8 7'}} eventHandlers={{click:()=>onSelect(i)}}><Tooltip>Alternative · {Math.round(r.travelMinutes)} min</Tooltip></Polyline>)}
 {route?.roadIds.map((id,i)=>{const a=route.nodes[i],b=route.nodes[i+1];const f=route.congestion[id]??1;return <Polyline key={`${id}-${i}`} positions={[coord(a),coord(b)]} pathOptions={{color:f>2?'#ec7751':f>1.5?'#e4b54d':'#098b7a',weight:7,opacity:.95}}><Tooltip>{f>2?'Heavy':f>1.5?'Moderate':'Light'} simulated traffic</Tooltip></Polyline>})}
 {network.places.filter(p=>p.category!=='LANDMARK').map(p=><CircleMarker key={p.id} center={coord(p.id)} radius={route?.stopId===p.id?10:5} pathOptions={{color:'white',weight:2,fillColor:p.category==='PETROL'?'#d99a33':p.category==='HOTEL'?'#8d76be':'#cc7968',fillOpacity:1}}><Tooltip>{p.name} · {p.category.toLowerCase()}</Tooltip></CircleMarker>)}
 {[request.source,request.destination].map((id,i)=><CircleMarker key={`${i}-${id}`} center={coord(id)} radius={10} pathOptions={{color:'white',weight:4,fillColor:i?'#233b42':'#078b76',fillOpacity:1}}><Tooltip permanent direction="top" offset={[0,-12]}>{i?'B':'A'} · {byId[id].name}</Tooltip></CircleMarker>)}
 <MapControls points={points}/></MapContainer><div className="map-label"><span className="live-dot"/> BHUBANESWAR <span>ODISHA, INDIA</span></div><div className="map-legend"><span><i style={{background:'#098b7a'}}/>Light</span><span><i style={{background:'#e4b54d'}}/>Moderate</span><span><i style={{background:'#ec7751'}}/>Heavy</span><small>Simulated traffic</small></div></div>
}

