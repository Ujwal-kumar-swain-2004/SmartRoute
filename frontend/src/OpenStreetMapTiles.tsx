import {useState} from 'react';
import {TileLayer} from 'react-leaflet';

/** Shared public OSM basemap. No API key, bulk download, or prefetch. */
export default function OpenStreetMapTiles(){
 const [failed,setFailed]=useState(false);
 const [attempt,setAttempt]=useState(0);
 return <><TileLayer key={attempt} url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19} attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors' eventHandlers={{tileerror:()=>setFailed(true)}}/>{failed&&<div className="tile-note" role="status">OpenStreetMap tiles could not load. Check your internet connection. <button type="button" onClick={()=>{setFailed(false);setAttempt(n=>n+1)}}>Retry map</button></div>}</>;
}
