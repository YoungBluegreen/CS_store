var loading = {};

loading.Particle = function( opt ) {
  this.radius = 7;
  this.x = opt.x;
  this.y = opt.y;
  this.angle = opt.angle;
  this.speed = opt.speed;
  this.accel = opt.accel;
  this.decay = 0.01;
  this.life = 1;
};

loading.Particle.prototype.step = function( i ) {
  this.speed += this.accel;
  this.x += Math.cos( this.angle ) * this.speed;
  this.y += Math.sin( this.angle ) * this.speed;
  this.angle += loading.PI / 64;
  this.accel *= 1.01;
  this.life -= this.decay;
  
  if( this.life <= 0 ) {
	loading.particles.splice( i, 1 );
  }
};

loading.Particle.prototype.draw = function( i ) {
  loading.ctx.fillStyle = loading.ctx.strokeStyle = 'hsla(' + ( loading.tick + ( this.life * 120 ) ) + ', 100%, 60%, ' + this.life + ')';
  loading.ctx.beginPath();
  if( loading.particles[ i - 1 ] ) {
	loading.ctx.moveTo( this.x, this.y );
	loading.ctx.lineTo( loading.particles[ i - 1 ].x, loading.particles[ i - 1 ].y );
  }
  loading.ctx.stroke();
  
  loading.ctx.beginPath();
  loading.ctx.arc( this.x, this.y, Math.max( 0.001, this.life * this.radius ), 0, loading.TWO_PI );
  loading.ctx.fill();
  
  var size = Math.random() * 1.25;
  loading.ctx.fillRect( ~~( this.x + ( ( Math.random() - 0.5 ) * 35 ) * this.life ), ~~( this.y + ( ( Math.random() - 0.5 ) * 35 ) * this.life ), size, size );
}

loading.step = function() {
  loading.particles.push( new loading.Particle({
	x: loading.width / 2 + Math.cos( loading.tick / 20 ) * loading.min / 2,
	y: loading.height / 2 + Math.sin( loading.tick / 20 ) * loading.min / 2,
	angle: loading.globalRotation + loading.globalAngle,
	speed: 0,
	accel: 0.01
  }));
  
  loading.particles.forEach( function( elem, index ) {
	elem.step( index );
  });
  
  loading.globalRotation += loading.PI / 6;
  loading.globalAngle += loading.PI / 6;
};

loading.draw = function() {
  loading.ctx.clearRect( 0, 0, loading.width, loading.height );
  
  loading.particles.forEach( function( elem, index ) {
	elem.draw( index );
  });
};

loading.loop = function() {
  if (loading.run) {  
    requestAnimationFrame( loading.loop );
  }
  loading.step();
  loading.draw();
  loading.tick++;
};

loading.stop = function() {
  loading.run = false;
};

loading.init = function(width, height) {
  loading.canvas = document.createElement( 'canvas' );
  loading.ctx = loading.canvas.getContext( '2d' );
  loading.width = width;
  loading.height = height;
  loading.canvas.width = loading.width * window.devicePixelRatio;
  loading.canvas.height = loading.height * window.devicePixelRatio;
  loading.canvas.style.width = loading.width + 'px';
  loading.canvas.style.height = loading.height + 'px';
  loading.ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  loading.min = loading.width * 0.5;
  loading.particles = [];
  loading.globalAngle = 0;
  loading.globalRotation = 0;
  loading.tick = 0;
  loading.run = true;
  loading.PI = Math.PI;
  loading.TWO_PI = loading.PI * 2;
  loading.ctx.globalCompositeOperation = 'lighter';
  return loading.canvas;
};